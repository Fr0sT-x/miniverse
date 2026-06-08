package dev.frost.miniverse.minigame.core;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.network.TransitionTransferCoordinator;
import dev.frost.miniverse.session.SessionConfigJson;
import dev.frost.miniverse.session.SessionRegistry;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class SessionBootstrapper {
    private static final Set<String> REGISTERED_GAME_IDS = new HashSet<>();
    private static final Map<String, State<?>> STATES = new HashMap<>();
    private static final int CLIENT_READY_TIMEOUT_TICKS = 60 * 20;

    private SessionBootstrapper() {
    }

    public static synchronized <T extends Minigame> void register(Handler<T> handler) {
        String gameId = handler.gameId();
        if (!REGISTERED_GAME_IDS.add(gameId)) {
            return;
        }

        State<T> state = new State<>(handler);
        STATES.put(gameId.toLowerCase(), state);
        ServerPlayConnectionEvents.JOIN.register((connectionHandler, sender, server) -> state.onJoin(connectionHandler.player));
    }

    public static void tick(MinecraftServer server) {
        State<?> state = stateForConfig();
        if (state == null) {
            return;
        }
        state.onTick(server);
    }

    public static void markClientReady(ServerPlayerEntity player, String sessionId) {
        State<?> state = stateForConfig();
        if (state != null) {
            state.markClientReady(player, sessionId);
        }
    }

    private static State<?> stateForConfig() {
        String configGame = System.getProperty("miniverse.session.game", "").trim().toLowerCase();
        if (configGame.isBlank()) {
            return null;
        }
        return STATES.get(configGame);
    }

    public interface Handler<T extends Minigame> {
        String gameId();

        Class<T> runtimeType();

        T createRuntime();

        void applySettings(T minigame, Properties properties);

        default void onPlayerJoin(T minigame, ServerPlayerEntity player, Properties properties) {
        }

        default MatchLifecycleOptions lifecycleOptions(T minigame, Properties properties) {
            return MatchLifecycleOptions.defaults(minigame.getName())
                .withStartTitle(Text.literal(minigame.getName()), Text.literal("Get ready."));
        }

        default Optional<Text> startFailureMessage(T minigame) {
            return Optional.empty();
        }

        boolean canStart(T minigame);
    }

    private static final class State<T extends Minigame> {
        private final Handler<T> handler;
        private Properties config;
        private boolean settingsApplied;
        private boolean loggedReady;
        private boolean loggedWaitingPlayers;
        private boolean loggedWaitingRoles;
        private boolean loggedWaitingClientReady;
        private boolean startupAborted;
        private boolean savedStateLoaded;
        private final Map<UUID, ClientReadyState> clientReadyStates = new HashMap<>();
        private final Map<UUID, Integer> loadingStartTicks = new HashMap<>();

        private State(Handler<T> handler) {
            this.handler = handler;
        }

        private void onJoin(ServerPlayerEntity player) {
            if (this.startupAborted) {
                player.networkHandler.disconnect(Text.literal("This match startup was cancelled. Please rejoin from the lobby."));
                return;
            }

            Properties properties = this.getConfig();
            if (!this.handler.gameId().equalsIgnoreCase(properties.getProperty("game", ""))) {
                return;
            }

            Properties effectiveProperties = this.withRegistryAssignments(properties);
            boolean expectedPlayer = properties.containsKey("player." + player.getUuid());
            boolean assignedLatePlayer = !expectedPlayer && effectiveProperties.containsKey("player." + player.getUuid());
            if (!expectedPlayer && !assignedLatePlayer && !this.acceptsLateJoin()) {
                return;
            }

            T minigame = this.getOrCreateRuntime();
            if (minigame == null) {
                return;
            }

            if (!this.settingsApplied) {
                this.handler.applySettings(minigame, properties);
                this.settingsApplied = true;
            }
            this.loadSavedStateIfPresent();

            boolean restoredActiveSession = SessionRestoreCoordinator.hasRestoredActiveOrPausedState();
            boolean knownRuntimeParticipant = MinigameManager.getInstance().isParticipant(player.getUuid());
            boolean admittedParticipant = expectedPlayer || assignedLatePlayer || knownRuntimeParticipant;
            if (admittedParticipant) {
                MinigameManager.getInstance().addParticipant(player);
            }
            boolean reconnectingActivePlayer = admittedParticipant && (this.isActiveOrPaused(minigame) || restoredActiveSession);
            if (expectedPlayer && !reconnectingActivePlayer) {
                this.markLoading(player);
            } else if (!admittedParticipant) {
                player.changeGameMode(GameMode.SPECTATOR);
                player.sendMessage(Text.literal("Joined active match as unassigned spectator. Ask an admin for a team assignment."), false);
            }
            if (admittedParticipant) {
                MatchLifecycleController.getInstance().onParticipantJoin(player);
                this.handler.onPlayerJoin(minigame, player, effectiveProperties);
                if (assignedLatePlayer && minigame instanceof DynamicParticipantMinigame dynamic) {
                    String team = effectiveProperties.getProperty("player." + player.getUuid() + ".team", effectiveProperties.getProperty("groupLabel", ""));
                    String role = effectiveProperties.getProperty(effectiveProperties.getProperty("game", this.handler.gameId()) + ".role." + player.getUuid(), "");
                    dynamic.addParticipantMidGame(player, team, role);
                }
            }
            if (reconnectingActivePlayer) {
                MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
                if (SessionRestoreCoordinator.restorePlayerStateIfPresent(runtime, player)) {
                    MinigameSessionStore.save(runtime, MinigameSessionStore.SaveReason.RECONNECT);
                }
                MinigameManager.getInstance().applyPauseStateToParticipant(player);
                this.releaseLoadedPlayer(player, effectiveProperties);
            } else if (expectedPlayer) {
                this.sendMatchIntro(player, minigame, properties);
                this.broadcastReadyState(properties, "Waiting for players...");
                this.maybeStart(minigame, properties);
            } else {
                MinigameManager.getInstance().applyPauseStateToParticipant(player);
                this.releaseLoadedPlayer(player, effectiveProperties);
            }
        }

        private boolean acceptsLateJoin() {
            MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
            if (runtime == null) {
                return false;
            }
            GameState state = runtime.state();
            return state == GameState.PAUSED || state.isActive();
        }

        private boolean isActiveOrPaused(T minigame) {
            GameState state = minigame.getState();
            return state == GameState.PAUSED || state.isActive();
        }

        private void onTick(MinecraftServer server) {
            if (this.startupAborted) {
                return;
            }

            Properties properties = this.getConfig();
            if (!this.handler.gameId().equalsIgnoreCase(properties.getProperty("game", ""))) {
                return;
            }

            T minigame = this.getOrCreateRuntime();
            if (minigame == null) {
                return;
            }

            if (!this.settingsApplied) {
                this.handler.applySettings(minigame, properties);
                this.settingsApplied = true;
            }
            this.loadSavedStateIfPresent();

            if (!this.loggedReady) {
                Miniverse.LOGGER.info("Session bootstrap ready for {}.", this.handler.gameId());
                this.loggedReady = true;
            }

            this.addOnlineExpectedPlayers(server, properties);
            if (minigame.getState() == GameState.WAITING_FOR_PLAYERS) {
                this.checkReadyTimeouts(server, properties);
            }
            this.maybeStart(minigame, properties);
        }

        private void loadSavedStateIfPresent() {
            if (this.savedStateLoaded) {
                return;
            }
            this.savedStateLoaded = true;
            MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
            SessionRestoreCoordinator.restoreRuntimeIfPresent(
                runtime,
                this.handler.lifecycleOptions(this.handler.runtimeType().cast(runtime.minigame()), this.getConfig()),
                runtime.minigame()::startGame
            );
        }

        private void addOnlineExpectedPlayers(MinecraftServer server, Properties properties) {
            for (String name : properties.stringPropertyNames()) {
                if (!isExpectedPlayerKey(properties, name)) {
                    continue;
                }

                UUID playerId;
                try {
                    playerId = UUID.fromString(name.substring("player.".length()));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                if (MinigameManager.getInstance().isParticipant(playerId)) {
                    continue;
                }

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    this.onJoin(player);
                }
            }
        }

        private void maybeStart(T minigame, Properties properties) {
            if (this.startupAborted) {
                return;
            }
            if (minigame.getState() != GameState.WAITING_FOR_PLAYERS) {
                return;
            }

            boolean expectedOnline = this.expectedPlayersOnline(properties);
            boolean clientsReady = this.expectedPlayersReady(properties);
            boolean canStart = this.handler.canStart(minigame);
            if (!expectedOnline) {
                if (!this.loggedWaitingPlayers) {
                    Miniverse.LOGGER.info("Session {} waiting for expected players to join.", this.handler.gameId());
                    this.loggedWaitingPlayers = true;
                }
                return;
            }
            if (!clientsReady) {
                if (!this.loggedWaitingClientReady) {
                    Miniverse.LOGGER.info("Session {} waiting for clients to finish loading.", this.handler.gameId());
                    this.loggedWaitingClientReady = true;
                }
                this.broadcastReadyState(properties, "Waiting for players...");
                return;
            }
            if (!canStart) {
                Optional<Text> failureMessage = this.handler.startFailureMessage(minigame);
                if (failureMessage.isPresent()) {
                    MinecraftServer server = MinigameManager.getInstance().getContext() == null ? null : MinigameManager.getInstance().getContext().nullableServer();
                    if (server != null) {
                        this.abortStartup(server, properties, failureMessage.get());
                    }
                    return;
                }
                if (!this.loggedWaitingRoles) {
                    Miniverse.LOGGER.info("Session {} waiting for role assignments/ready state.", this.handler.gameId());
                    this.loggedWaitingRoles = true;
                }
                return;
            }

            MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
            if (runtime != null) {
                this.unfreezeLoadingPlayers(properties);
                MatchLifecycleOptions options = this.handler.lifecycleOptions(minigame, properties);
                MatchLifecycleController.getInstance().beginMatch(runtime, options, minigame::startGame);
            }
        }

        private void markClientReady(ServerPlayerEntity player, String sessionId) {
            if (this.startupAborted) {
                return;
            }

            Properties properties = this.getConfig();
            String configuredSession = properties.getProperty("sessionId", "");
            if (!configuredSession.isBlank() && !configuredSession.equals(sessionId)) {
                return;
            }
            if (!this.handler.gameId().equalsIgnoreCase(properties.getProperty("game", ""))) {
                return;
            }
            if (!properties.containsKey("player." + player.getUuid())) {
                return;
            }

            this.clientReadyStates.put(player.getUuid(), ClientReadyState.READY);
            this.loadingStartTicks.remove(player.getUuid());
            this.loggedWaitingClientReady = false;
            this.broadcastReadyState(properties, "Ready");

            T minigame = this.getOrCreateRuntime();
            if (minigame != null) {
                if (minigame.getState().isActive() || minigame.getState().isTerminal()) {
                    this.releaseLoadedPlayer(player, properties);
                    return;
                }
                this.maybeStart(minigame, properties);
            }
        }

        private void markLoading(ServerPlayerEntity player) {
            this.clientReadyStates.put(player.getUuid(), ClientReadyState.LOADING);
            MinecraftServer server = player.getEntityWorld().getServer();
            this.loadingStartTicks.put(player.getUuid(), server == null ? 0 : server.getTicks());
            FreezeService.getInstance().freeze(player, FreezeReason.MATCH_LOADING);
        }

        private void releaseLoadedPlayer(ServerPlayerEntity player, Properties properties) {
            FreezeService.getInstance().unfreeze(player, FreezeReason.MATCH_LOADING);
            if (ServerPlayNetworking.canSend(player, NetworkConstants.MATCH_START_ID)) {
                ServerPlayNetworking.send(player, new NetworkConstants.MatchStartPayload(properties.getProperty("sessionId", "")));
            }
        }

        private boolean expectedPlayersReady(Properties properties) {
            for (UUID uuid : this.expectedPlayerIds(properties)) {
                if (this.clientReadyStates.get(uuid) != ClientReadyState.READY) {
                    return false;
                }
            }
            return true;
        }

        private void checkReadyTimeouts(MinecraftServer server, Properties properties) {
            int now = server.getTicks();
            for (UUID uuid : this.expectedPlayerIds(properties)) {
                if (this.clientReadyStates.get(uuid) == ClientReadyState.READY) {
                    continue;
                }

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player == null || player.isDisconnected()) {
                    continue;
                }

                int started = this.loadingStartTicks.computeIfAbsent(uuid, ignored -> now);
                if (now - started < CLIENT_READY_TIMEOUT_TICKS) {
                    continue;
                }

                Miniverse.LOGGER.warn("Disconnecting {} from session {} after client ready timeout.", player.getName().getString(), properties.getProperty("sessionId", ""));
                player.networkHandler.disconnect(Text.literal("Timed out while loading match resources. Please rejoin the session."));
                this.abortStartup(server, properties, Text.literal("Match cancelled because " + player.getName().getString() + " did not finish loading in time."));
                return;
            }
        }

        private void abortStartup(MinecraftServer server, Properties properties, Text message) {
            this.startupAborted = true;
            this.clientReadyStates.clear();
            this.loadingStartTicks.clear();

            String sessionId = properties.getProperty("sessionId", "");
            if (!sessionId.isBlank()) {
                SessionRegistry.markStopRequested(sessionId);
            }

            String host = SessionRuntimeConfig.getReturnHost();
            int port = SessionRuntimeConfig.getReturnPort();
            Miniverse.LOGGER.warn("Aborting {} session startup: {}", this.handler.gameId(), message.getString());
            for (UUID uuid : this.expectedPlayerIds(properties)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player == null || player.isDisconnected()) {
                    continue;
                }

                FreezeService.getInstance().unfreeze(player, FreezeReason.MATCH_LOADING);
                player.sendMessage(message, false);
                if (host != null && !host.isBlank() && port > 0) {
                    TransitionTransferCoordinator.transfer(player, host, port, "Returning to Lobby");
                } else if (ServerPlayNetworking.canSend(player, NetworkConstants.MATCH_START_ID)) {
                    ServerPlayNetworking.send(player, new NetworkConstants.MatchStartPayload(sessionId));
                }
            }

            MinigameManager.getInstance().reset();
        }

        private void unfreezeLoadingPlayers(Properties properties) {
            MinecraftServer server = MinigameManager.getInstance().getContext() == null ? null : MinigameManager.getInstance().getContext().nullableServer();
            if (server == null) {
                return;
            }
            for (UUID uuid : this.expectedPlayerIds(properties)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    FreezeService.getInstance().unfreeze(player, FreezeReason.MATCH_LOADING);
                }
            }
        }

        private void sendMatchIntro(ServerPlayerEntity player, T minigame, Properties properties) {
            if (!ServerPlayNetworking.canSend(player, NetworkConstants.MATCH_INTRO_ID)) {
                return;
            }

            MatchLifecycleOptions options = this.handler.lifecycleOptions(minigame, properties);
            ServerPlayNetworking.send(player, new NetworkConstants.MatchIntroPayload(this.matchIntroData(properties, options)));
        }

        private NbtCompound matchIntroData(Properties properties, MatchLifecycleOptions options) {
            String gameId = properties.getProperty("game", this.handler.gameId());
            NbtCompound data = new NbtCompound();
            data.putString("sessionId", properties.getProperty("sessionId", ""));
            data.putString("gameId", gameId);
            data.putString("title", options.startTitle().getString());
            data.putString("description", options.startSubtitle().getString());
            data.putString("map", properties.getProperty("map", properties.getProperty("world", "Generated Arena")));
            data.putInt("readyPlayers", this.readyCount(properties));
            data.putInt("totalPlayers", this.expectedPlayerIds(properties).size());
            data.put("teams", this.teamIntroData(properties));
            return data;
        }

        private NbtList teamIntroData(Properties properties) {
            Map<String, NbtCompound> teams = new LinkedHashMap<>();
            for (UUID uuid : this.expectedPlayerIds(properties)) {
                String teamId = this.introTeamId(properties, uuid);
                NbtCompound team = teams.computeIfAbsent(teamId, id -> {
                    NbtCompound created = new NbtCompound();
                    created.putString("id", id);
                    created.putString("name", displayName(id));
                    created.putInt("color", teamColor(id));
                    created.put("players", new NbtList());
                    return created;
                });

                NbtCompound member = new NbtCompound();
                member.putString("uuid", uuid.toString());
                member.putString("name", playerName(properties, uuid));
                member.putBoolean("ready", this.clientReadyStates.get(uuid) == ClientReadyState.READY);
                team.getList("players", NbtElement.COMPOUND_TYPE).add(member);
            }

            NbtList list = new NbtList();
            teams.values().forEach(list::add);
            return list;
        }

        private String introTeamId(Properties properties, UUID uuid) {
            String gameId = properties.getProperty("game", this.handler.gameId());
            String role = properties.getProperty(gameId + ".role." + uuid, "").trim();
            if (!role.isBlank()) {
                return roleTeamId(role);
            }
            return properties.getProperty("player." + uuid + ".team", "Players");
        }

        private void broadcastReadyState(Properties properties, String status) {
            MinecraftServer server = MinigameManager.getInstance().getContext() == null ? null : MinigameManager.getInstance().getContext().nullableServer();
            if (server == null) {
                return;
            }
            String sessionId = properties.getProperty("sessionId", "");
            int ready = this.readyCount(properties);
            int total = this.expectedPlayerIds(properties).size();
            for (UUID uuid : this.expectedPlayerIds(properties)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null && ServerPlayNetworking.canSend(player, NetworkConstants.MATCH_READY_STATE_ID)) {
                    NbtCompound data = new NbtCompound();
                    data.put("teams", this.teamIntroData(properties));
                    ServerPlayNetworking.send(player, new NetworkConstants.MatchReadyStatePayload(sessionId, ready, total, status, data));
                }
            }
        }

        private int readyCount(Properties properties) {
            int ready = 0;
            for (UUID uuid : this.expectedPlayerIds(properties)) {
                if (this.clientReadyStates.get(uuid) == ClientReadyState.READY) {
                    ready++;
                }
            }
            return ready;
        }

        private List<UUID> expectedPlayerIds(Properties properties) {
            return properties.stringPropertyNames().stream()
                .filter(name -> isExpectedPlayerKey(properties, name))
                .map(name -> name.substring("player.".length()))
                .map(SessionBootstrapper.State::parseUuid)
                .filter(uuid -> uuid != null)
                .toList();
        }

        private static UUID parseUuid(String value) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private static String playerName(Properties properties, UUID uuid) {
            String prefix = "player." + uuid + ".";
            return properties.getProperty(prefix + "name", uuid.toString().substring(0, 8));
        }

        private static String roleTeamId(String role) {
            String normalized = role.trim().toLowerCase().replace(' ', '_');
            return normalized.endsWith("s") ? normalized : normalized + "s";
        }

        private static String displayName(String id) {
            if (id == null || id.isBlank()) {
                return "Players";
            }
            String normalized = id.replace('_', ' ').replace('-', ' ').trim();
            StringBuilder builder = new StringBuilder();
            for (String part : normalized.split("\\s+")) {
                if (part.isBlank()) {
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    builder.append(part.substring(1).toLowerCase());
                }
            }
            return builder.isEmpty() ? id : builder.toString();
        }

        private static int teamColor(String id) {
            String normalized = id == null ? "" : id.toLowerCase();
            if (normalized.contains("red") || normalized.contains("hunter")) {
                return 0xFFE11D48;
            }
            if (normalized.contains("blue") || normalized.contains("runner")) {
                return 0xFF2563EB;
            }
            if (normalized.contains("green")) {
                return 0xFF16A34A;
            }
            if (normalized.contains("yellow")) {
                return 0xFFFACC15;
            }
            return 0xFF38BDF8;
        }

        private T getOrCreateRuntime() {
            Minigame active = MinigameManager.getInstance().getActiveMinigame();
            if (this.handler.runtimeType().isInstance(active)) {
                return this.handler.runtimeType().cast(active);
            }

            if (active != null) {
                Miniverse.LOGGER.warn(
                    "Session config requested {} but active minigame is {}.",
                    this.handler.gameId(),
                    active.getName()
                );
                return null;
            }

            T minigame = this.handler.createRuntime();
            MinigameManager.getInstance().setActiveMinigame(minigame);
            return minigame;
        }

        private boolean expectedPlayersOnline(Properties properties) {
            for (String name : properties.stringPropertyNames()) {
                if (!isExpectedPlayerKey(properties, name)) {
                    continue;
                }

                try {
                    UUID uuid = UUID.fromString(name.substring("player.".length()));
                    if (!MinigameManager.getInstance().isParticipant(uuid)) {
                        return false;
                    }
                } catch (IllegalArgumentException ignored) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isExpectedPlayerKey(Properties properties, String name) {
            return name.startsWith("player.") && "true".equalsIgnoreCase(properties.getProperty(name));
        }

        private synchronized Properties getConfig() {
            if (this.config != null) {
                return this.config;
            }

            this.config = new Properties();
            String configPath = System.getProperty("miniverse.session.config", "");
            if (configPath.isBlank()) {
                return this.config;
            }

            this.config = SessionConfigJson.readRuntimeProperties(Path.of(configPath));
            return this.config;
        }

        private Properties withRegistryAssignments(Properties base) {
            String sessionId = base.getProperty("sessionId", "");
            if (sessionId.isBlank()) {
                return base;
            }
            Properties merged = new Properties();
            merged.putAll(base);
            Properties registry = SessionRegistry.loadRuntimeProperties(sessionId);
            for (String name : registry.stringPropertyNames()) {
                if (name.startsWith("player.") || name.contains(".role.")) {
                    merged.setProperty(name, registry.getProperty(name));
                }
            }
            return merged;
        }
    }

    private enum ClientReadyState {
        LOADING,
        READY
    }
}
