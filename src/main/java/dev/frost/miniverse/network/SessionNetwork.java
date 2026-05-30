package dev.frost.miniverse.network;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameRegistry;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.session.GameSession;
import dev.frost.miniverse.session.SessionCreationService;
import dev.frost.miniverse.session.SessionGroup;
import dev.frost.miniverse.session.PendingSessionJoinManager;
import dev.frost.miniverse.session.SessionLauncherConfig;
import dev.frost.miniverse.session.SessionRegistry;
import dev.frost.miniverse.session.SessionManager;
import dev.frost.miniverse.session.SessionPermissions;
import dev.frost.miniverse.session.SessionMemoryConfig;
import dev.frost.miniverse.session.SessionRetentionConfig;
import dev.frost.miniverse.session.SessionServerConfig;
import dev.frost.miniverse.session.SessionConfigJson;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

public final class SessionNetwork {
    private static boolean registered;

    private SessionNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.REQUEST_SESSIONS_ID, (payload, context) -> handleRequest(context.server(), context.player()));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CREATE_SESSION_ID, (payload, context) -> handleCreate(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.LAUNCH_SESSION_ID, (payload, context) -> handleLaunch(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.STOP_SESSION_ID, (payload, context) -> handleStop(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.PAUSE_SESSION_ID, (payload, context) -> handlePause(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.ASSIGN_MID_GAME_PLAYER_ID, (payload, context) -> handleAssignMidGame(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.INSPECT_SESSION_ID, (payload, context) -> handleInspect(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.RELAUNCH_SESSION_ID, (payload, context) -> handleRelaunch(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.DELETE_SESSION_ID, (payload, context) -> handleDeleteRetained(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CHANGE_SEED_ID, (payload, context) -> handleChangeSeed(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CLEANUP_PLAYER_ID, (payload, context) -> handleCleanupPlayer(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.LAUNCHER_SETTINGS_ID, (payload, context) -> handleLauncherSettings(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.SERVER_SETTINGS_ID, (payload, context) -> handleServerSettings(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CLIENT_CONNECTION_HOST_ID, (payload, context) ->
            ClientConnectionHosts.remember(context.player(), payload.host())
        );
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CLIENT_MATCH_READY_ID, (payload, context) ->
            SessionBootstrapper.markClientReady(context.player(), payload.sessionId())
        );

        registered = true;
    }

    private static void handleRequest(MinecraftServer server, ServerPlayerEntity player) {
        if (!SessionPermissions.checkCanManageSessions(player, "view sessions")) {
            return;
        }
        sendSessionList(server, player);
    }

    private static void handleCreate(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.CreateSessionPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "create sessions")) {
            return;
        }

        SessionManager manager = SessionManager.getInstance();
        SessionCreationService.CreateResult result = new SessionCreationService(manager)
            .create(server, player, payload.game(), payload.name(), payload.plan());
        if (!result.succeeded()) {
            player.sendMessage(Text.literal(result.errorMessage()), false);
            return;
        }

        GameSession session = result.session();
        player.sendMessage(Text.literal("Created session " + session.getSessionId() + " for " + result.gameType().getDisplayName() + "."), false);
        if (!result.autoLaunch()) {
            sendSessionList(server, player);
            return;
        }

        player.sendMessage(Text.literal("Launching session " + session.getSessionId() + "..."), false);
        broadcastLaunchProgress(server, session, "Queued", "Session launch requested by " + player.getName().getString(), 8, false);
        manager.launchSession(session.getSessionId(), server).whenComplete((launched, error) -> server.execute(() -> {
            if (error != null) {
                broadcastLaunchProgress(server, session, "Failed", error.getMessage(), 100, true);
                player.sendMessage(Text.literal("Failed to launch session " + session.getSessionId() + ": " + error.getMessage()), false);
                sendSessionList(server, player);
                return;
            }

            broadcastLaunchProgress(server, launched, "Transferring players", "Moving players to the session server", 100, true);
            manager.transferAssignedPlayers(server, launched);
            player.sendMessage(Text.literal("Launched session " + launched.getSessionId() + "."), false);
            sendSessionList(server, player);
        }));
    }

    private static void handleLaunch(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.LaunchSessionPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "launch sessions")) {
            return;
        }

        SessionManager manager = SessionManager.getInstance();
        String sessionId = payload.sessionId();

        if (manager.getSession(sessionId).isEmpty()) {
            player.sendMessage(Text.literal("Unknown session '" + sessionId + "'."), false);
            return;
        }

        player.sendMessage(Text.literal("Launching session " + sessionId + "..."), false);
        manager.getSession(sessionId).ifPresent(session -> broadcastLaunchProgress(server, session, "Queued", "Session launch requested by " + player.getName().getString(), 8, false));
        manager.launchSession(sessionId, server).whenComplete((session, error) -> server.execute(() -> {
            if (error != null) {
                manager.getSession(sessionId).ifPresent(failed -> broadcastLaunchProgress(server, failed, "Failed", error.getMessage(), 100, true));
                player.sendMessage(Text.literal("Failed to launch session " + sessionId + ": " + error.getMessage()), false);
                sendSessionList(server, player);
                return;
            }

            broadcastLaunchProgress(server, session, "Transferring players", "Moving players to the session server", 100, true);
            manager.transferAssignedPlayers(server, session);
            player.sendMessage(Text.literal("Launched session " + session.getSessionId() + "."), false);
            sendSessionList(server, player);
        }));
    }

    private static void handleStop(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.StopSessionPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "stop sessions")) {
            return;
        }

        String sessionId = payload.sessionId();
        SessionRegistry.markStopRequested(sessionId);
        player.sendMessage(Text.literal("Stopping session " + sessionId + " and returning players to the main server..."), false);
        sendSessionList(server, player);
    }

    private static void handlePause(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.PauseSessionPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, payload.paused() ? "pause sessions" : "resume sessions")) {
            return;
        }

        String sessionId = payload.sessionId();
        if (!SessionRuntimeConfig.isSessionServer() && SessionManager.getInstance().getSession(sessionId).isEmpty()) {
            player.sendMessage(Text.literal("Unknown session '" + sessionId + "'."), false);
            return;
        }

        if (payload.paused()) {
            SessionRegistry.markPauseRequested(sessionId);
            player.sendMessage(Text.literal("Pause requested for session " + sessionId + "."), false);
        } else {
            SessionRegistry.clearPauseRequested(sessionId);
            player.sendMessage(Text.literal("Resume requested for session " + sessionId + "."), false);
        }
        sendSessionList(server, player);
    }

    private static void handleAssignMidGame(MinecraftServer server, ServerPlayerEntity admin, NetworkConstants.AssignMidGamePlayerPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(admin, "assign late players")) {
            return;
        }

        UUID playerId;
        try {
            playerId = UUID.fromString(payload.playerUuid());
        } catch (IllegalArgumentException e) {
            admin.sendMessage(Text.literal("Invalid player id: " + payload.playerUuid()).formatted(Formatting.RED), false);
            return;
        }

        if (SessionRuntimeConfig.isSessionServer()) {
            String currentSessionId = SessionRuntimeConfig.getSessionId().orElse(payload.sessionId());
            if (!currentSessionId.equals(payload.sessionId())) {
                admin.sendMessage(Text.literal("Cannot assign players to a different backend session from here.").formatted(Formatting.RED), false);
                return;
            }
            if (!validateMidGameRoleForSession(payload.sessionId(), payload.role(), admin)) {
                sendSessionList(server, admin);
                return;
            }
            SessionRegistry.addMidGameAssignmentRequest(
                payload.sessionId(),
                playerId,
                payload.teamLabel(),
                payload.role(),
                admin.getName().getString()
            );
            admin.sendMessage(Text.literal("Assignment requested. The main server will transfer the player into this session.").formatted(Formatting.YELLOW), false);
            sendSessionList(server, admin);
            return;
        }

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(playerId);
        if (target == null) {
            admin.sendMessage(Text.literal("That player is no longer online.").formatted(Formatting.YELLOW), false);
            PendingSessionJoinManager.getInstance().remove(playerId);
            sendSessionList(server, admin);
            return;
        }

        SessionManager manager = SessionManager.getInstance();
        try {
            if (!validateMidGameRoleForSession(payload.sessionId(), payload.role(), admin)) {
                sendSessionList(server, admin);
                return;
            }
            manager.assignPlayerMidGameAsync(payload.sessionId(), target, payload.teamLabel(), payload.role(), server)
                .whenComplete((group, error) -> server.execute(() -> {
                    if (error != null) {
                        admin.sendMessage(Text.literal(error.getCause() == null ? error.getMessage() : error.getCause().getMessage()).formatted(Formatting.RED), false);
                        sendSessionList(server, admin);
                        return;
                    }

                    PendingSessionJoinManager.getInstance().remove(playerId);
                    manager.transferPlayer(target, group);
                    admin.sendMessage(Text.literal("Assigned " + target.getName().getString() + " to " + payload.sessionId() + "."), false);
                    sendSessionList(server, admin);
                }));
        } catch (RuntimeException e) {
            admin.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
            sendSessionList(server, admin);
            return;
        }
    }

    private static boolean validateMidGameRoleForSession(String sessionId, String role, ServerPlayerEntity admin) {
        if (!isManhuntSession(sessionId)) {
            return true;
        }
        String normalized = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        boolean valid = normalized.equals("speedrunner") || normalized.equals("runner") || normalized.equals("hunter");
        if (!valid) {
            admin.sendMessage(Text.literal("Manhunt late joins require choosing Speedrunner or Hunter before transfer.").formatted(Formatting.RED), false);
        }
        return valid;
    }

    private static boolean isManhuntSession(String sessionId) {
        if (SessionRuntimeConfig.isSessionServer()) {
            return SessionRuntimeConfig.getSessionJson()
                .map(json -> "manhunt".equalsIgnoreCase(SessionConfigJson.string(json, "gameId", SessionConfigJson.string(json, "game", ""))))
                .orElse(false);
        }
        return SessionManager.getInstance().getSession(sessionId)
            .map(session -> "manhunt".equalsIgnoreCase(session.getGameType().getCommandName()))
            .orElse(false);
    }

    private static void handleChangeSeed(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.ChangeSeedPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "change session seed")) {
            return;
        }

        String sessionId = payload.sessionId();
        if (!SessionRuntimeConfig.isSessionServer() && SessionManager.getInstance().getSession(sessionId).isEmpty()) {
            player.sendMessage(Text.literal("Unknown session '" + sessionId + "'."), false);
            return;
        }

        SessionRegistry.markSeedChangeRequested(sessionId);
        Text message = Text.literal("Seed is changing for session " + sessionId + ". A new world will generate in the background.").formatted(Formatting.GREEN);
        player.sendMessage(message, false);
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);
        sendSessionList(server, player);
    }

    private static void handleInspect(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.InspectSessionPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "inspect retained sessions")) {
            return;
        }

        String sessionId = payload.sessionId();
        player.sendMessage(Text.literal("Launching inspection copy for session " + sessionId + "..."), false);
        SessionManager.getInstance().launchInspectionAsync(sessionId, player).whenComplete((result, error) -> server.execute(() -> {
            if (error != null) {
                player.sendMessage(Text.literal("Failed to launch inspection copy for " + sessionId + ": " + error.getMessage()).formatted(Formatting.RED), false);
                sendSessionList(server, player);
                return;
            }

            String context = "Inspecting " + sessionId;
            if (VelocityProxyBridge.isEnabled()) {
                TransitionTransferCoordinator.transferToVelocityBackend(
                    player,
                    VelocityProxyBridge.serverName(sessionId, "inspection-" + result.port()),
                    result.port(),
                    context,
                    () -> {
                    }
                );
            } else {
                TransitionTransferCoordinator.transfer(
                    player,
                    SessionServerConfig.getInstance().advertisedHost(),
                    SessionLauncherConfig.getInstance().publicPortForLocalPort(result.port()),
                    context
                );
            }
            player.sendMessage(Text.literal("Inspection copy launched for " + sessionId + "."), false);
            sendSessionList(server, player);
        }));
    }

    private static void handleRelaunch(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.RelaunchSessionPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "relaunch sessions")) {
            return;
        }
        SessionManager manager = SessionManager.getInstance();
        String sessionId = payload.sessionId();
        if (manager.getSession(sessionId).isPresent()) {
            player.sendMessage(Text.literal("Session " + sessionId + " is already running."), false);
            return;
        }
        player.sendMessage(Text.literal("Relaunching session " + sessionId + "..."), false);
        manager.relaunchRetainedSession(sessionId, server)
            .whenComplete((session, error) -> server.execute(() -> {
                if (error != null) {
                    player.sendMessage(Text.literal("Failed to relaunch session " + sessionId + ": " + error.getMessage()), false);
                    sendSessionList(server, player);
                    return;
                }
                player.sendMessage(Text.literal("Relaunched session " + sessionId + "."), false);
                sendSessionList(server, player);
            }));
    }

    private static void handleDeleteRetained(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.DeleteSessionPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "delete sessions")) {
            return;
        }
        String sessionId = payload.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            player.sendMessage(Text.literal("Invalid session id."), false);
            return;
        }
        if (SessionManager.getInstance().getSession(sessionId).isPresent()) {
            player.sendMessage(Text.literal("Session " + sessionId + " is still active. Stop it before deleting."), false);
            return;
        }
        if (SessionRegistry.loadSnapshot(sessionId).isEmpty()) {
            player.sendMessage(Text.literal("Unknown session '" + sessionId + "'."), false);
            return;
        }
        SessionRegistry.removeSession(sessionId);
        player.sendMessage(Text.literal("Deleted retained session " + sessionId + "."), false);
        sendSessionList(server, player);
    }

    public static void broadcastLaunchProgress(MinecraftServer server, GameSession session, String stage, String detail, int progress, boolean done) {
        String title = session.getGameType().getDisplayName() + " " + session.getSessionId();
        server.execute(() -> {
            Set<UUID> recipients = new LinkedHashSet<>();
            for (SessionGroup group : session.snapshotGroups()) {
                recipients.addAll(group.getPlayerUuids());
            }
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                if (SessionPermissions.canManageSessions(online)) {
                    recipients.add(online.getUuid());
                }
            }
            sendLaunchProgress(server, recipients, session.getSessionId(), title, stage, detail, progress, done);
        });
    }

    private static void broadcastLaunchProgress(MinecraftServer server, String sessionId, String title, String stage, String detail, int progress, boolean done) {
        server.execute(() -> {
            Set<UUID> recipients = new LinkedHashSet<>();
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                if (SessionPermissions.canManageSessions(online)) {
                    recipients.add(online.getUuid());
                }
            }
            sendLaunchProgress(server, recipients, sessionId, title, stage, detail, progress, done);
        });
    }

    private static void sendLaunchProgress(MinecraftServer server, Set<UUID> recipients, String sessionId, String title, String stage, String detail, int progress, boolean done) {
        NetworkConstants.LaunchProgressPayload payload = new NetworkConstants.LaunchProgressPayload(sessionId, title, stage, detail == null ? "" : detail, Math.clamp(progress, 0, 100), done);
        Text message = Text.literal(title + ": " + stage).formatted(done ? Formatting.GREEN : Formatting.YELLOW);
        for (UUID recipient : recipients) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(recipient);
            if (target == null) {
                continue;
            }
            ServerPlayNetworking.send(target, payload);
            if (progress <= 10 || done) {
                target.sendMessage(message, false);
            }
        }
    }
    private static void handleCleanupPlayer(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.CleanupPlayerPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "run session cleanup")) {
            return;
        }

        // Clear the player's inventory
        player.getInventory().clear();
        player.currentScreenHandler.sendContentUpdates();

        // Fill the player's hunger
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0F);

        player.sendMessage(Text.literal("Your inventory has been cleaned and hunger restored."), false);
    }

    private static void handleLauncherSettings(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.LauncherSettingsPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "update launcher settings")) {
            return;
        }

        int maxConcurrentLaunches = getIntOrDefault(payload.settings(), "maxConcurrentLaunches", SessionLauncherConfig.getInstance().maxConcurrentLaunches());
        SessionManager.getInstance().setMaxConcurrentLaunches(maxConcurrentLaunches);
        int applied = SessionLauncherConfig.getInstance().maxConcurrentLaunches();
        player.sendMessage(Text.literal("Max concurrent session launches set to " + applied + "."), false);
        sendSessionList(server, player);
    }

    private static void handleServerSettings(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.ServerSettingsPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "update server settings")) {
            return;
        }

        NbtCompound settings = payload.settings();
        NbtCompound memory = getCompoundOrEmpty(settings, "memory");
        NbtCompound serverSettings = getCompoundOrEmpty(settings, "server");
        NbtCompound retentionSettings = getCompoundOrEmpty(settings, "retention");

        SessionMemoryConfig memoryConfig = SessionMemoryConfig.getInstance();
        if (memory.contains("enabled", NbtElement.NUMBER_TYPE)) {
            memoryConfig.setEnabled(memory.getBoolean("enabled"));
        }
        if (memory.contains("maxHeapGb", NbtElement.NUMBER_TYPE)) {
            memoryConfig.setMaxHeap(memory.getInt("maxHeapGb"));
        }
        if (memory.contains("initialHeapGb", NbtElement.NUMBER_TYPE)) {
            memoryConfig.setInitialHeap(memory.getInt("initialHeapGb"));
        }

        SessionServerConfig serverConfig = SessionServerConfig.getInstance();
        if (serverSettings.contains("viewDistance", NbtElement.NUMBER_TYPE)) {
            serverConfig.setViewDistance(serverSettings.getInt("viewDistance"));
        }
        if (serverSettings.contains("simulationDistance", NbtElement.NUMBER_TYPE)) {
            serverConfig.setSimulationDistance(serverSettings.getInt("simulationDistance"));
        }
        if (serverSettings.contains("onlineMode", NbtElement.NUMBER_TYPE)) {
            serverConfig.setOnlineMode(serverSettings.getBoolean("onlineMode"));
        }
        if (serverSettings.contains("spawnProtection", NbtElement.NUMBER_TYPE)) {
            serverConfig.setSpawnProtection(serverSettings.getInt("spawnProtection"));
        }
        if (serverSettings.contains("difficulty", NbtElement.STRING_TYPE)) {
            serverConfig.setDifficulty(serverSettings.getString("difficulty"));
        }
        if (serverSettings.contains("allowFlight", NbtElement.NUMBER_TYPE)) {
            serverConfig.setAllowFlight(serverSettings.getBoolean("allowFlight"));
        }
        if (serverSettings.contains("acceptsTransfers", NbtElement.NUMBER_TYPE)) {
            serverConfig.setAcceptsTransfers(serverSettings.getBoolean("acceptsTransfers"));
        }
        if (serverSettings.contains("advertisedHost", NbtElement.STRING_TYPE)) {
            serverConfig.setAdvertisedHost(serverSettings.getString("advertisedHost"));
        }

        SessionRetentionConfig retentionConfig = SessionRetentionConfig.getInstance();
        if (retentionSettings.contains("keepLatestSessions", NbtElement.NUMBER_TYPE)) {
            retentionConfig.setKeepLatestSessions(retentionSettings.getInt("keepLatestSessions"));
        }
        if (retentionSettings.contains("maxAgeDays", NbtElement.NUMBER_TYPE)) {
            retentionConfig.setMaxAgeDays(retentionSettings.getInt("maxAgeDays"));
        }

        sendSessionList(server, player);
    }

    private static void sendSessionList(MinecraftServer server, ServerPlayerEntity player) {
        NbtCompound root = new NbtCompound();
        NbtList sessions = new NbtList();

        if (SessionRuntimeConfig.isSessionServer()) {
            SessionRuntimeConfig.getSessionJson()
                .map(SessionNetwork::runtimeSessionToNbt)
                .ifPresent(sessions::add);
        }

        List<SessionRegistry.Snapshot> snapshots = sessions.isEmpty() ? SessionRegistry.loadSnapshots() : List.of();
        if (snapshots.isEmpty()) {
            for (GameSession session : sessions.isEmpty() ? SessionManager.getInstance().getSessions() : List.<GameSession>of()) {
                NbtCompound entry = new NbtCompound();
                entry.putString("id", session.getSessionId());
                entry.putString("game", session.getGameType().getDisplayName());
                entry.putString("state", session.getState().name());
                entry.putLong("seed", session.getSeedPlan().sharedSeed());
                entry.putLong("createdAt", session.getCreatedAt().toEpochMilli());
                entry.putLong("launchedAt", session.getLaunchedAt() == null ? 0L : session.getLaunchedAt().toEpochMilli());
                entry.putLong("updatedAt", System.currentTimeMillis());
                entry.putLong("playedMillis", session.getLaunchedAt() == null ? 0L : Math.max(0L, System.currentTimeMillis() - session.getLaunchedAt().toEpochMilli()));
                entry.putBoolean("inspectable", false);
                entry.putBoolean("retained", false);

                NbtList players = new NbtList();
                session.getAssignments().forEach(assignment -> players.add(NbtString.of(assignment.getDisplayName())));
                entry.put("players", players);
                entry.putInt("playerCount", session.getAssignments().stream().mapToInt(SessionGroup::getPlayerCount).sum());
                addGroups(entry, session.getAssignments());
                sessions.add(entry);
            }
        } else {
            for (SessionRegistry.Snapshot snapshot : snapshots) {
                NbtCompound entry = new NbtCompound();
                entry.putString("id", snapshot.sessionId());
                entry.putString("game", snapshot.game());
                entry.putString("state", snapshot.state());
                entry.putLong("seed", snapshot.seed());
                entry.putLong("createdAt", snapshot.createdAtMillis());
                entry.putLong("launchedAt", snapshot.launchedAtMillis());
                entry.putLong("updatedAt", snapshot.updatedAtMillis());
                entry.putLong("playedMillis", snapshot.playedMillis());
                entry.putBoolean("inspectable", snapshot.inspectable());
                entry.putBoolean("retained", SessionManager.getInstance().getSession(snapshot.sessionId()).isEmpty());

                NbtList players = new NbtList();
                for (String name : snapshot.players()) {
                    players.add(NbtString.of(name));
                }
                entry.put("players", players);
                entry.putInt("playerCount", snapshot.playerCount());
                SessionManager.getInstance().getSession(snapshot.sessionId())
                    .ifPresent(session -> addGroups(entry, session.getAssignments()));
                sessions.add(entry);
            }
        }

        root.put("sessions", sessions);

        NbtList games = new NbtList();
        for (MinigameDefinition definition : MinigameRegistry.getDefinitions()) {
            games.add(definition.metadata().toNbt());
        }
        root.put("games", games);

        NbtList roster = new NbtList();
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("uuid", online.getUuidAsString());
            entry.putString("name", online.getName().getString());
            roster.add(entry);
        }
        root.put("players", roster);

        NbtList pendingJoiners = new NbtList();
        List<PendingSessionJoinManager.PendingJoiner> pending = SessionRuntimeConfig.isSessionServer()
            ? SessionRuntimeConfig.getSessionId()
                .map(sessionId -> SessionRegistry.listPendingJoinNotices(sessionId).stream()
                    .map(notice -> new PendingSessionJoinManager.PendingJoiner(notice.playerId(), notice.playerName(), notice.joinedAtMillis()))
                    .toList())
                .orElse(List.of())
            : PendingSessionJoinManager.getInstance().list(server);
        for (PendingSessionJoinManager.PendingJoiner pendingJoiner : pending) {
            NbtCompound entry = new NbtCompound();
            entry.putString("uuid", pendingJoiner.playerId().toString());
            entry.putString("name", pendingJoiner.playerName());
            entry.putLong("joinedAt", pendingJoiner.joinedAtMillis());
            pendingJoiners.add(entry);
        }
        root.put("pendingJoiners", pendingJoiners);

        NbtCompound launcher = new NbtCompound();
        SessionLauncherConfig launcherConfig = SessionLauncherConfig.getInstance();
        launcher.putInt("maxConcurrentLaunches", launcherConfig.maxConcurrentLaunches());
        launcher.putInt("queueCapacity", launcherConfig.queueCapacity());
        launcher.putInt("sessionPortStart", launcherConfig.sessionPortStart());
        launcher.putInt("sessionPortEnd", launcherConfig.sessionPortEnd());
        launcher.putInt("publicSessionPortStart", launcherConfig.publicSessionPortStart());
        launcher.putInt("publicSessionPortEnd", launcherConfig.publicSessionPortEnd());
        root.put("launcher", launcher);

        NbtCompound memory = new NbtCompound();
        SessionMemoryConfig memoryConfig = SessionMemoryConfig.getInstance();
        memory.putBoolean("enabled", memoryConfig.isEnabled());
        memory.putInt("maxHeapGb", memoryConfig.getMaxHeap());
        memory.putInt("initialHeapGb", memoryConfig.getInitialHeap());
        root.put("memory", memory);

        NbtCompound serverSettings = new NbtCompound();
        SessionServerConfig serverConfig = SessionServerConfig.getInstance();
        serverSettings.putInt("viewDistance", serverConfig.viewDistance());
        serverSettings.putInt("simulationDistance", serverConfig.simulationDistance());
        serverSettings.putBoolean("onlineMode", serverConfig.onlineMode());
        serverSettings.putInt("spawnProtection", serverConfig.spawnProtection());
        serverSettings.putString("difficulty", serverConfig.difficulty());
        serverSettings.putBoolean("allowFlight", serverConfig.allowFlight());
        serverSettings.putBoolean("acceptsTransfers", serverConfig.acceptsTransfers());
        serverSettings.putString("advertisedHost", serverConfig.advertisedHost());
        root.put("server", serverSettings);

        NbtCompound retention = new NbtCompound();
        SessionRetentionConfig retentionConfig = SessionRetentionConfig.getInstance();
        retention.putInt("keepLatestSessions", retentionConfig.keepLatestSessions());
        retention.putInt("maxAgeDays", retentionConfig.maxAgeDays());
        root.put("retention", retention);

        root.putBoolean("sessionServer", SessionRuntimeConfig.isSessionServer());

        ServerPlayNetworking.send(player, new NetworkConstants.SessionListPayload(root));
    }

    private static int getIntOrDefault(NbtCompound nbt, String key, int fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE)
            ? nbt.getInt(key)
            : fallback;
    }

    private static NbtCompound getCompoundOrEmpty(NbtCompound nbt, String key) {
        return nbt != null && nbt.contains(key, NbtElement.COMPOUND_TYPE)
            ? nbt.getCompound(key)
            : new NbtCompound();
    }

    private static NbtCompound runtimeSessionToNbt(JsonObject json) {
        NbtCompound entry = new NbtCompound();
        String sessionId = SessionConfigJson.string(json, "sessionId", "");
        entry.putString("id", sessionId);
        entry.putString("game", SessionConfigJson.string(json, "gameDisplayName", SessionConfigJson.string(json, "gameId", "")));
        entry.putString("state", SessionRegistry.isPauseRequested(sessionId) ? "PAUSED" : "RUNNING");
        entry.putLong("seed", SessionConfigJson.longValue(json, "seed", 0L));
        entry.putLong("createdAt", SessionConfigJson.longValue(json, "createdAt", 0L));
        entry.putLong("launchedAt", SessionConfigJson.longValue(json, "launchedAt", 0L));
        entry.putLong("updatedAt", System.currentTimeMillis());
        entry.putLong("playedMillis", Math.max(0L, System.currentTimeMillis() - SessionConfigJson.longValue(json, "launchedAt", System.currentTimeMillis())));
        entry.putBoolean("inspectable", false);
        entry.putBoolean("retained", false);
        entry.putInt("playerCount", runtimePlayerCount(json));
        entry.put("groups", runtimeGroups(json));
        return entry;
    }

    private static void addGroups(NbtCompound entry, List<SessionGroup> groups) {
        NbtList groupList = new NbtList();
        for (SessionGroup group : groups) {
            NbtCompound groupNbt = new NbtCompound();
            groupNbt.putString("label", group.getGroupLabel());
            groupNbt.putString("displayName", group.getDisplayName());
            groupNbt.putString("state", group.getState().name());
            groupNbt.putInt("playerCount", group.getPlayerCount());
            groupList.add(groupNbt);
        }
        entry.put("groups", groupList);
    }

    private static NbtList runtimeGroups(JsonObject json) {
        JsonArray teams = json.has("teams") && json.get("teams").isJsonArray()
            ? json.getAsJsonArray("teams")
            : new JsonArray();
        NbtList groupList = new NbtList();
        for (var teamElement : teams) {
            if (!teamElement.isJsonObject()) {
                continue;
            }
            JsonObject team = teamElement.getAsJsonObject();
            NbtCompound groupNbt = new NbtCompound();
            String label = SessionConfigJson.string(team, "label", "");
            String displayName = SessionConfigJson.string(team, "displayName", label);
            groupNbt.putString("label", label);
            groupNbt.putString("displayName", displayName);
            groupNbt.putString("state", SessionConfigJson.string(team, "state", "RUNNING"));
            groupNbt.putInt("playerCount", team.has("members") && team.get("members").isJsonArray()
                ? team.getAsJsonArray("members").size()
                : SessionConfigJson.integer(team, "playerCount", 0));
            groupList.add(groupNbt);
        }
        return groupList;
    }

    private static int runtimePlayerCount(JsonObject json) {
        JsonArray teams = json.has("teams") && json.get("teams").isJsonArray()
            ? json.getAsJsonArray("teams")
            : new JsonArray();

        int count = 0;
        for (var teamElement : teams) {
            if (!teamElement.isJsonObject()) {
                continue;
            }
            JsonObject team = teamElement.getAsJsonObject();
            if (team.has("members") && team.get("members").isJsonArray()) {
                count += team.getAsJsonArray("members").size();
            } else {
                count += SessionConfigJson.integer(team, "playerCount", 0);
            }
        }
        return count;
    }
}
