package dev.frost.miniverse.minigame.core;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class SessionBootstrapper {
    private static final Set<String> REGISTERED_GAME_IDS = new HashSet<>();
    private static final Map<String, State<?>> STATES = new HashMap<>();

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
        private final SessionConfigParser configParser;
        private final ClientReadinessCoordinator readinessCoordinator;
        private final SessionRuntimeInitializer<T> runtimeInitializer;
        private final SessionFlagPoller flagPoller;

        private boolean loggedReady;
        private boolean loggedWaitingPlayers;
        private boolean loggedWaitingRoles;
        private boolean loggedWaitingClientReady;

        private State(Handler<T> handler) {
            this.handler = handler;
            this.configParser = new SessionConfigParser();
            this.readinessCoordinator = new ClientReadinessCoordinator(this.configParser, handler.gameId());
            this.runtimeInitializer = new SessionRuntimeInitializer<>(handler);
            this.flagPoller = new SessionFlagPoller(this.configParser, this.readinessCoordinator, handler.gameId());
        }

        private void onJoin(ServerPlayerEntity player) {
            if (this.flagPoller.isStartupAborted()) {
                player.networkHandler.disconnect(Text.literal("This match startup was cancelled. Please rejoin from the lobby."));
                return;
            }

            Properties properties = this.configParser.getConfig();
            if (!this.handler.gameId().equalsIgnoreCase(properties.getProperty("game", ""))) {
                return;
            }

            Properties effectiveProperties = this.configParser.getEffectiveProperties();
            boolean expectedPlayer = properties.containsKey("player." + player.getUuid());
            boolean assignedLatePlayer = !expectedPlayer && effectiveProperties.containsKey("player." + player.getUuid());
            if (!expectedPlayer && !assignedLatePlayer && !this.acceptsLateJoin()) {
                return;
            }

            T minigame = this.runtimeInitializer.getOrCreateRuntime();
            if (minigame == null) {
                return;
            }

            this.runtimeInitializer.applySettingsIfNecessary(minigame, properties);
            this.runtimeInitializer.loadSavedStateIfPresent(properties);

            boolean restoredActiveSession = SessionRestoreCoordinator.hasRestoredActiveOrPausedState();
            boolean knownRuntimeParticipant = MinigameManager.getInstance().isParticipant(player.getUuid());
            boolean admittedParticipant = expectedPlayer || assignedLatePlayer || knownRuntimeParticipant;
            if (admittedParticipant) {
                MinigameManager.getInstance().addParticipant(player);
            }

            boolean reconnectingActivePlayer = admittedParticipant && (this.isActiveOrPaused(minigame) || restoredActiveSession);
            if (expectedPlayer && !reconnectingActivePlayer) {
                this.readinessCoordinator.markLoading(player);
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
                this.readinessCoordinator.releaseLoadedPlayer(player, effectiveProperties);
            } else if (expectedPlayer) {
                this.readinessCoordinator.sendMatchIntro(player, properties, this.handler.lifecycleOptions(minigame, properties));
                this.readinessCoordinator.broadcastReadyState(player.getServer(), properties, "Waiting for players...");
                this.maybeStart(minigame, properties);
            } else {
                MinigameManager.getInstance().applyPauseStateToParticipant(player);
                this.readinessCoordinator.releaseLoadedPlayer(player, effectiveProperties);
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
            if (this.flagPoller.isStartupAborted()) {
                return;
            }

            Properties properties = this.configParser.getConfig();
            if (!this.handler.gameId().equalsIgnoreCase(properties.getProperty("game", ""))) {
                return;
            }

            T minigame = this.runtimeInitializer.getOrCreateRuntime();
            if (minigame == null) {
                return;
            }

            this.runtimeInitializer.applySettingsIfNecessary(minigame, properties);
            this.runtimeInitializer.loadSavedStateIfPresent(properties);

            if (!this.loggedReady) {
                Miniverse.LOGGER.info("Session bootstrap ready for {}.", this.handler.gameId());
                this.loggedReady = true;
            }

            this.addOnlineExpectedPlayers(server, properties);
            if (minigame.getState() == GameState.WAITING_FOR_PLAYERS) {
                boolean timedOut = this.readinessCoordinator.checkReadyTimeouts(server, properties, () -> {
                    this.flagPoller.abortStartup(server, properties, Text.literal("Match cancelled because someone did not finish loading in time."));
                });
                if (timedOut) return;
            }
            this.maybeStart(minigame, properties);
        }

        private void addOnlineExpectedPlayers(MinecraftServer server, Properties properties) {
            for (UUID playerId : this.configParser.getExpectedPlayerIds(properties)) {
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
            if (this.flagPoller.isStartupAborted()) {
                return;
            }
            if (minigame.getState() != GameState.WAITING_FOR_PLAYERS) {
                return;
            }

            boolean expectedOnline = this.expectedPlayersOnline(properties);
            boolean clientsReady = this.readinessCoordinator.areExpectedPlayersReady(properties);
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
                MinecraftServer server = MinigameManager.getInstance().getContext() == null ? null : MinigameManager.getInstance().getContext().nullableServer();
                this.readinessCoordinator.broadcastReadyState(server, properties, "Waiting for players...");
                return;
            }
            if (!canStart) {
                Optional<Text> failureMessage = this.handler.startFailureMessage(minigame);
                if (failureMessage.isPresent()) {
                    MinecraftServer server = MinigameManager.getInstance().getContext() == null ? null : MinigameManager.getInstance().getContext().nullableServer();
                    if (server != null) {
                        this.flagPoller.abortStartup(server, properties, failureMessage.get());
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
                MinecraftServer server = MinigameManager.getInstance().getContext() == null ? null : MinigameManager.getInstance().getContext().nullableServer();
                this.readinessCoordinator.unfreezeLoadingPlayers(server, properties);
                MatchLifecycleOptions options = this.handler.lifecycleOptions(minigame, properties);
                MatchLifecycleController.getInstance().beginMatch(runtime, options, minigame::startGame);
            }
        }

        private void markClientReady(ServerPlayerEntity player, String sessionId) {
            if (this.flagPoller.isStartupAborted()) {
                return;
            }

            Properties properties = this.configParser.getConfig();
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

            this.readinessCoordinator.markClientReady(player.getUuid());
            this.loggedWaitingClientReady = false;
            MinecraftServer server = player.getServer();
            this.readinessCoordinator.broadcastReadyState(server, properties, "Ready");

            T minigame = this.runtimeInitializer.getOrCreateRuntime();
            if (minigame != null) {
                if (minigame.getState().isActive() || minigame.getState().isTerminal()) {
                    this.readinessCoordinator.releaseLoadedPlayer(player, properties);
                    return;
                }
                this.maybeStart(minigame, properties);
            }
        }

        private boolean expectedPlayersOnline(Properties properties) {
            for (UUID uuid : this.configParser.getExpectedPlayerIds(properties)) {
                if (!MinigameManager.getInstance().isParticipant(uuid)) {
                    return false;
                }
            }
            return true;
        }
    }
}
