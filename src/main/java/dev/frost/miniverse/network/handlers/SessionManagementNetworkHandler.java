package dev.frost.miniverse.network.handlers;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.session.GameSession;
import dev.frost.miniverse.session.LaunchProgressBroadcaster;
import dev.frost.miniverse.session.PlayerTransferService;
import dev.frost.miniverse.session.SessionCreationService;
import dev.frost.miniverse.session.SessionListSerializer;
import dev.frost.miniverse.session.SessionManager;
import dev.frost.miniverse.session.SessionPermissions;
import dev.frost.miniverse.session.SessionRegistry;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SessionManagementNetworkHandler {
    private static final java.util.Set<String> pendingDeletions = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.REQUEST_SESSIONS_ID, (payload, context) -> handleRequest(context.server(), context.player()));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CREATE_SESSION_ID, (payload, context) -> handleCreate(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.LAUNCH_SESSION_ID, (payload, context) -> handleLaunch(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.STOP_SESSION_ID, (payload, context) -> handleStop(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.PAUSE_SESSION_ID, (payload, context) -> handlePause(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.INSPECT_SESSION_ID, (payload, context) -> handleInspect(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.RELAUNCH_SESSION_ID, (payload, context) -> handleRelaunch(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.DELETE_SESSION_ID, (payload, context) -> handleDeleteRetained(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.DELETE_ALL_SESSIONS_ID, (payload, context) -> handleDeleteAll(context.server(), context.player(), payload));
    }

    private static void handleRequest(MinecraftServer server, ServerPlayerEntity player) {
        if (!SessionPermissions.checkCanManageSessions(player, "view sessions")) {
            return;
        }
        SessionListSerializer.sendSessionList(server, player);
        KitNetworkHandler.syncKitsToPlayer(server, player);
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
            SessionListSerializer.sendSessionList(server, player);
            return;
        }

        player.sendMessage(Text.literal("Launching session " + session.getSessionId() + "..."), false);
        LaunchProgressBroadcaster.broadcastLaunchProgress(server, session, "Queued", "Session launch requested by " + player.getName().getString(), 8, false);
        manager.launchSession(session.getSessionId(), server).whenComplete((launched, error) -> server.execute(() -> {
            if (error != null) {
                LaunchProgressBroadcaster.broadcastLaunchProgress(server, session, "Failed", error.getMessage(), 100, true);
                player.sendMessage(Text.literal("Failed to launch session " + session.getSessionId() + ": " + error.getMessage()), false);
                SessionListSerializer.sendSessionList(server, player);
                return;
            }

            LaunchProgressBroadcaster.broadcastLaunchProgress(server, launched, "Transferring players", "Moving players to the session server", 100, true);
            new PlayerTransferService().transferAssignedPlayers(server, launched);
            player.sendMessage(Text.literal("Launched session " + launched.getSessionId() + "."), false);
            SessionListSerializer.sendSessionList(server, player);
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
        manager.getSession(sessionId).ifPresent(session -> LaunchProgressBroadcaster.broadcastLaunchProgress(server, session, "Queued", "Session launch requested by " + player.getName().getString(), 8, false));
        manager.launchSession(sessionId, server).whenComplete((session, error) -> server.execute(() -> {
            if (error != null) {
                manager.getSession(sessionId).ifPresent(failed -> LaunchProgressBroadcaster.broadcastLaunchProgress(server, failed, "Failed", error.getMessage(), 100, true));
                player.sendMessage(Text.literal("Failed to launch session " + sessionId + ": " + error.getMessage()), false);
                SessionListSerializer.sendSessionList(server, player);
                return;
            }

            LaunchProgressBroadcaster.broadcastLaunchProgress(server, session, "Transferring players", "Moving players to the session server", 100, true);
            new PlayerTransferService().transferAssignedPlayers(server, session);
            player.sendMessage(Text.literal("Launched session " + session.getSessionId() + "."), false);
            SessionListSerializer.sendSessionList(server, player);
        }));
    }

    private static void handleStop(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.StopSessionPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "stop sessions")) {
            return;
        }

        String sessionId = payload.sessionId();
        SessionRegistry.markStopRequested(sessionId);
        player.sendMessage(Text.literal("Stopping session " + sessionId + " and returning players to the main server..."), false);
        SessionListSerializer.sendSessionList(server, player);
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
        SessionListSerializer.sendSessionList(server, player);
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
                SessionListSerializer.sendSessionList(server, player);
                return;
            }

            new PlayerTransferService().transferToInspectionServer(player, sessionId, result.port());
            player.sendMessage(Text.literal("Inspection copy launched for " + sessionId + "."), false);
            SessionListSerializer.sendSessionList(server, player);
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
                    SessionListSerializer.sendSessionList(server, player);
                    return;
                }
                LaunchProgressBroadcaster.broadcastLaunchProgress(server, session, "Transferring players", "Moving players to the session server", 100, true);
                new PlayerTransferService().transferAssignedPlayers(server, session);
                player.sendMessage(Text.literal("Relaunched session " + sessionId + "."), false);
                SessionListSerializer.sendSessionList(server, player);
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
        if (!pendingDeletions.add(sessionId)) {
            return;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> SessionRegistry.removeSession(sessionId))
            .whenComplete((ignored, error) -> server.execute(() -> {
                pendingDeletions.remove(sessionId);
                if (error != null) {
                    dev.frost.miniverse.Miniverse.LOGGER.warn("Failed to delete session {}", sessionId, error);
                    player.sendMessage(Text.literal("Failed to delete session " + sessionId + ": " + error.getMessage()), false);
                } else {
                    player.sendMessage(Text.literal("Deleted retained session " + sessionId + "."), false);
                }
                SessionListSerializer.sendSessionList(server, player);
            }));
    }

    private static void handleDeleteAll(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.DeleteAllSessionsPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "delete sessions")) {
            return;
        }
        java.util.List<String> sessionIds = payload.sessionIds().stream()
            .filter(id -> id != null && !id.isBlank())
            .filter(id -> SessionManager.getInstance().getSession(id).isEmpty())
            .filter(id -> SessionRegistry.loadSnapshot(id).isPresent())
            .filter(pendingDeletions::add)
            .toList();

        if (sessionIds.isEmpty()) {
            SessionListSerializer.sendSessionList(server, player);
            return;
        }

        player.sendMessage(Text.literal("Deleting " + sessionIds.size() + " retained session(s)..."), false);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            for (String sessionId : sessionIds) {
                try {
                    SessionRegistry.removeSession(sessionId);
                } catch (Exception e) {
                    dev.frost.miniverse.Miniverse.LOGGER.warn("Failed to delete session {} during bulk delete", sessionId, e);
                } finally {
                    pendingDeletions.remove(sessionId);
                }
            }
        }).whenComplete((ignored, error) -> server.execute(() -> {
            if (error != null) {
                dev.frost.miniverse.Miniverse.LOGGER.warn("Bulk session delete encountered an error", error);
                player.sendMessage(Text.literal("Bulk delete encountered an error. Some sessions may not have been deleted."), false);
            } else {
                player.sendMessage(Text.literal("Deleted " + sessionIds.size() + " session(s)."), false);
            }
            SessionListSerializer.sendSessionList(server, player);
        }));
    }
}
