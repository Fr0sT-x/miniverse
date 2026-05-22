package dev.frost.miniverse.session;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.network.TransitionTransferCoordinator;
import dev.frost.miniverse.network.VelocityProxyBridge;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SessionRoutingEvents {
    private static final long RETURN_RETRY_DELAY_MS = 2000L;
    private static volatile long nextReturnAttemptMs;
    private static final Set<String> announcedSeedChanges = ConcurrentHashMap.newKeySet();
    private static final Set<String> completedSeedChangeTransfers = ConcurrentHashMap.newKeySet();

    private SessionRoutingEvents() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            grantDevSessionOperator(handler.player, server);

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

    private static void grantDevSessionOperator(ServerPlayerEntity player, MinecraftServer server) {
        if (!SessionPermissions.isBackendDevSessionBypassEnabled()) {
            return;
        }

        if (server.getPlayerManager().isOperator(player.getGameProfile())) {
            return;
        }

        server.getPlayerManager().addToOperators(player.getGameProfile());
        Miniverse.LOGGER.info("Granted dev session operator access to {} ({})", player.getName().getString(), player.getUuidAsString());
    }

    private static void onServerTick(MinecraftServer server) {
        if (SessionRuntimeConfig.isSessionServer()) {
            handleSessionServerStop(server);
            return;
        }

        handleMainServerStop(server);
    }

    private static void handleSessionServerStop(MinecraftServer server) {
        SessionRuntimeConfig.getSessionId().ifPresent(sessionId -> {
            if (SessionRegistry.isSeedChangeRequested(sessionId)) {
                handleSessionServerSeedChange(server, sessionId);
                return;
            }
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
            List<ServerPlayerEntity> players = playersToReturn(minigameManager, server);

            Miniverse.LOGGER.info("Session server {} received stop request; returning players to {}:{}", sessionId, returnHost, returnPort);
            if (players.isEmpty()) {
                minigameManager.reset();
                SessionRegistry.markReturnComplete(sessionId);
                return;
            }

            AtomicInteger pendingTransfers = new AtomicInteger(players.size());
            for (ServerPlayerEntity player : players) {
                Miniverse.LOGGER.info("Returning player {} ({}) to {}:{}", player.getName().getString(), player.getUuidAsString(), returnHost, returnPort);
                sessionManager.transferPlayer(player, returnHost, returnPort, () -> {
                    if (pendingTransfers.decrementAndGet() == 0) {
                        minigameManager.reset();
                        SessionRegistry.markReturnComplete(sessionId);
                    }
                });
            }
        });
    }

    private static void handleSessionServerSeedChange(MinecraftServer server, String sessionId) {
        announceSeedChange(server, sessionId);

        if (!SessionRegistry.isStopRequested(sessionId)) {
            return;
        }

        String groupLabel = SessionRuntimeConfig.getGroupLabel();
        SessionRegistry.getSeedChangeTarget(sessionId, groupLabel).ifPresent(target -> {
            String completionKey = sessionId + "/" + groupLabel;
            if (!completedSeedChangeTransfers.add(completionKey)) {
                return;
            }

            Miniverse.LOGGER.info("Session server {} transferring players directly to replacement seed backend {}:{}.", sessionId, target.host(), target.port());
            List<ServerPlayerEntity> players = List.copyOf(server.getPlayerManager().getPlayerList());
            if (players.isEmpty()) {
                SessionRegistry.markSeedChangeTransferComplete(sessionId, groupLabel);
                return;
            }

            AtomicInteger pendingTransfers = new AtomicInteger(players.size());
            for (ServerPlayerEntity player : players) {
                Runnable afterTransfer = () -> {
                    if (pendingTransfers.decrementAndGet() == 0) {
                        SessionRegistry.markSeedChangeTransferComplete(sessionId, groupLabel);
                    }
                };
                if (VelocityProxyBridge.isEnabled()) {
                    TransitionTransferCoordinator.transferToVelocityBackend(
                        player,
                        VelocityProxyBridge.serverName(sessionId, groupLabel),
                        target.port(),
                        "Changing Seed",
                        afterTransfer
                    );
                } else {
                    TransitionTransferCoordinator.transfer(player, target.host(), target.port(), "Changing Seed", afterTransfer);
                }
            }
        });
    }

    private static void announceSeedChange(MinecraftServer server, String sessionId) {
        if (!announcedSeedChanges.add(sessionId)) {
            return;
        }

        Text message = Text.literal("Seed is changing. A new world is generating in the background.").formatted(Formatting.GREEN);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(message, false);
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);
        }
    }

    private static List<ServerPlayerEntity> playersToReturn(MinigameManager minigameManager, MinecraftServer server) {
        List<ServerPlayerEntity> participants = minigameManager.getParticipants();
        if (!participants.isEmpty()) {
            return participants;
        }

        return List.copyOf(server.getPlayerManager().getPlayerList());
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

    private static void handleMainServerStop(MinecraftServer server) {
        SessionManager sessionManager = SessionManager.getInstance();
        sessionManager.reapDeadBackends(server);

        List<SessionRegistry.StopState> stopStates = SessionRegistry.loadStopStates();
        if (stopStates.isEmpty()) {
            return;
        }

        for (SessionRegistry.StopState stopState : stopStates) {
            GameSession session = sessionManager.getSession(stopState.sessionId()).orElse(null);
            if (session == null) {
                if (stopState.returnComplete()) {
                    SessionRegistry.removeSession(stopState.sessionId());
                }
                continue;
            }

            if (stopState.stopRequested() && !stopState.seedChangeRequested()) {
                if (session.getState() != SessionState.STOPPING && session.getState() != SessionState.STOPPED) {
                    session.setState(SessionState.STOPPING);
                }
                sessionManager.clearSessionGroupsForSession(stopState.sessionId());
            }

            if (stopState.seedChangeRequested() && !stopState.stopRequested()) {
                Miniverse.LOGGER.info("Main server staging session {} with a new seed.", stopState.sessionId());
                SessionRegistry.markStopRequested(stopState.sessionId());
                sessionManager.stageSeedChangeRelaunch(stopState.sessionId(), server).whenComplete((launchedSession, error) -> server.execute(() -> {
                    if (error != null) {
                        Miniverse.LOGGER.warn("Failed to stage session {} with a new seed.", stopState.sessionId(), error);
                    }
                }));
                continue;
            }

            if (stopState.seedChangeRequested() && stopState.stopRequested()) {
                List<String> expectedGroups = sessionManager.expectedSeedChangeCompletionGroups(stopState.sessionId());
                if (SessionRegistry.areSeedChangeTransfersComplete(stopState.sessionId(), expectedGroups)) {
                    Miniverse.LOGGER.info("Main server completing seed change cleanup for session {}.", stopState.sessionId());
                    sessionManager.completeSeedChangeRelaunch(stopState.sessionId());
                }
                continue;
            }

            if (stopState.returnComplete()) {
                Miniverse.LOGGER.info("Main server archiving session {} after return complete.", stopState.sessionId());
                sessionManager.archiveSession(stopState.sessionId());
            }
        }
    }
}
