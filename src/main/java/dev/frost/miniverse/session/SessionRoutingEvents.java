package dev.frost.miniverse.session;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.Miniverse;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

public final class SessionRoutingEvents {
    private static final long RETURN_RETRY_DELAY_MS = 2000L;
    private static volatile long nextReturnAttemptMs;

    private SessionRoutingEvents() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SessionManager manager = SessionManager.getInstance();
            manager.getSessionForPlayer(handler.player.getUuid())
                .flatMap(session -> {
                    if (SessionRegistry.isStopRequested(session.getSessionId()) || SessionRegistry.isReturnComplete(session.getSessionId())) {
                        return java.util.Optional.empty();
                    }
                    return session.getAssignment(handler.player.getUuid());
                })
                .filter(assignment -> assignment.getState() == SessionState.RUNNING)
                .ifPresent(assignment -> manager.transferPlayer(handler.player, assignment));
        });

        ServerTickEvents.END_SERVER_TICK.register(SessionRoutingEvents::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (SessionRuntimeConfig.isSessionServer()) {
            handleSessionServerStop();
            return;
        }

        handleMainServerStop();
    }

    private static void handleSessionServerStop() {
        SessionRuntimeConfig.getSessionId().ifPresent(sessionId -> {
            if (!SessionRegistry.isStopRequested(sessionId)) {
                return;
            }
            if (SessionRegistry.isReturnComplete(sessionId)) {
                return;
            }

            String returnHost = SessionRuntimeConfig.getReturnHost();
            int returnPort = SessionRuntimeConfig.getReturnPort();
            if (!isHubAcceptingConnections(returnHost, returnPort)) {
                return;
            }

            MinigameManager minigameManager = MinigameManager.getInstance();
            SessionManager sessionManager = SessionManager.getInstance();

            Miniverse.LOGGER.info("Session server {} received stop request; returning players to {}:{}", sessionId, returnHost, returnPort);
            for (ServerPlayerEntity player : minigameManager.getParticipants()) {
                Miniverse.LOGGER.info("Returning player {} ({}) to {}:{}", player.getName().getString(), player.getUuidAsString(), returnHost, returnPort);
                sessionManager.transferPlayer(player, returnHost, returnPort);
            }

            minigameManager.reset();
            SessionRegistry.markReturnComplete(sessionId);
        });
    }

    private static boolean isHubAcceptingConnections(String host, int port) {
        long now = System.currentTimeMillis();
        if (now < nextReturnAttemptMs) {
            return false;
        }

        nextReturnAttemptMs = now + RETURN_RETRY_DELAY_MS;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void handleMainServerStop() {
        List<SessionRegistry.StopState> stopStates = SessionRegistry.loadStopStates();
        if (stopStates.isEmpty()) {
            return;
        }

        SessionManager sessionManager = SessionManager.getInstance();
        for (SessionRegistry.StopState stopState : stopStates) {
            GameSession session = sessionManager.getSession(stopState.sessionId()).orElse(null);
            if (session == null) {
                if (stopState.returnComplete()) {
                    SessionRegistry.removeSession(stopState.sessionId());
                }
                continue;
            }

            if (stopState.stopRequested()) {
                if (session.getState() != SessionState.STOPPING && session.getState() != SessionState.STOPPED) {
                    session.setState(SessionState.STOPPING);
                }
                sessionManager.clearPlayerAssignmentsForSession(stopState.sessionId());
            }

            if (stopState.returnComplete()) {
                Miniverse.LOGGER.info("Main server cleaning up session {} after return complete.", stopState.sessionId());
                sessionManager.removeSession(stopState.sessionId());
            }
        }
    }
}
