package dev.frost.miniverse.network;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameRegistry;
import dev.frost.miniverse.session.GameSession;
import dev.frost.miniverse.session.SessionCreationService;
import dev.frost.miniverse.session.SessionGroup;
import dev.frost.miniverse.session.SessionLauncherConfig;
import dev.frost.miniverse.session.SessionRegistry;
import dev.frost.miniverse.session.SessionManager;
import dev.frost.miniverse.session.SessionPermissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

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
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.GRANT_OP_ID, (payload, context) -> handleGrantOp(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CLEANUP_PLAYER_ID, (payload, context) -> handleCleanupPlayer(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.LAUNCHER_SETTINGS_ID, (payload, context) -> handleLauncherSettings(context.server(), context.player(), payload));

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
        manager.launchSession(session.getSessionId()).whenComplete((launched, error) -> server.execute(() -> {
            if (error != null) {
                player.sendMessage(Text.literal("Failed to launch session " + session.getSessionId() + ": " + error.getMessage()), false);
                sendSessionList(server, player);
                return;
            }

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
        manager.launchSession(sessionId).whenComplete((session, error) -> server.execute(() -> {
            if (error != null) {
                player.sendMessage(Text.literal("Failed to launch session " + sessionId + ": " + error.getMessage()), false);
                sendSessionList(server, player);
                return;
            }

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

    private static void handleGrantOp(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.GrantOpPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "grant operator access")) {
            return;
        }

        // Grant operator permission to the player
        server.getPlayerManager().addToOperators(new net.minecraft.server.PlayerConfigEntry(player.getGameProfile()));
        player.sendMessage(Text.literal("You have been granted operator access."), false);
    }

    private static void handleCleanupPlayer(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.CleanupPlayerPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "run session cleanup")) {
            return;
        }

        // Clear the player's inventory
        player.getInventory().clear();

        // Fill the player's hunger
        player.getHungerManager().setFoodLevel(20);

        player.sendMessage(Text.literal("Your inventory has been cleaned and hunger restored."), false);
    }

    private static void handleLauncherSettings(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.LauncherSettingsPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "update launcher settings")) {
            return;
        }

        int maxConcurrentLaunches = payload.settings().getInt("maxConcurrentLaunches").orElse(SessionLauncherConfig.getInstance().maxConcurrentLaunches());
        SessionManager.getInstance().setMaxConcurrentLaunches(maxConcurrentLaunches);
        int applied = SessionLauncherConfig.getInstance().maxConcurrentLaunches();
        player.sendMessage(Text.literal("Max concurrent session launches set to " + applied + "."), false);
        sendSessionList(server, player);
    }

    private static void sendSessionList(MinecraftServer server, ServerPlayerEntity player) {
        NbtCompound root = new NbtCompound();
        NbtList sessions = new NbtList();

        List<SessionRegistry.Snapshot> snapshots = SessionRegistry.loadSnapshots();
        if (snapshots.isEmpty()) {
            for (GameSession session : SessionManager.getInstance().getSessions()) {
                NbtCompound entry = new NbtCompound();
                entry.putString("id", session.getSessionId());
                entry.putString("game", session.getGameType().getDisplayName());
                entry.putString("state", session.getState().name());
                entry.putLong("seed", session.getSeedPlan().sharedSeed());

                NbtList players = new NbtList();
                session.getAssignments().forEach(assignment -> players.add(NbtString.of(assignment.getDisplayName())));
                entry.put("players", players);
                entry.putInt("playerCount", session.getAssignments().stream().mapToInt(SessionGroup::getPlayerCount).sum());
                sessions.add(entry);
            }
        } else {
            for (SessionRegistry.Snapshot snapshot : snapshots) {
                NbtCompound entry = new NbtCompound();
                entry.putString("id", snapshot.sessionId());
                entry.putString("game", snapshot.game());
                entry.putString("state", snapshot.state());
                entry.putLong("seed", snapshot.seed());

                NbtList players = new NbtList();
                for (String name : snapshot.players()) {
                    players.add(NbtString.of(name));
                }
                entry.put("players", players);
                entry.putInt("playerCount", snapshot.playerCount());
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

        NbtCompound launcher = new NbtCompound();
        SessionLauncherConfig launcherConfig = SessionLauncherConfig.getInstance();
        launcher.putInt("maxConcurrentLaunches", launcherConfig.maxConcurrentLaunches());
        launcher.putInt("queueCapacity", launcherConfig.queueCapacity());
        root.put("launcher", launcher);

        ServerPlayNetworking.send(player, new NetworkConstants.SessionListPayload(root));
    }
}
