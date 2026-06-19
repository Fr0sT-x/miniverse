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
import dev.frost.miniverse.minigame.core.GameState;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SessionRoutingEvents {
    private static final long RETURN_RETRY_DELAY_MS = 2000L;
    private static final long BACKEND_NOTICE_POLL_MS = 2000L;
    private static final long RETURN_CONFIRM_TIMEOUT_MS = 20000L;
    private static final long MISSED_TRANSFER_GRACE_MS = 6000L;
    private static final long MISSED_TRANSFER_RETRY_MS = 10000L;
    private static volatile long nextReturnAttemptMs;
    private static volatile long nextBackendNoticePollMs;
    private static final Set<String> announcedSeedChanges = ConcurrentHashMap.newKeySet();
    private static final Set<String> completedSeedChangeTransfers = ConcurrentHashMap.newKeySet();
    private static final Map<String, ReturnTransferState> pendingReturnTransfers = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> deliveredPendingJoinNotices = new HashMap<>();
    private static final Map<TransferRetryKey, Long> lastMissedTransferAttemptMs = new ConcurrentHashMap<>();

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
                .ifPresent(assignment -> new dev.frost.miniverse.session.PlayerTransferService().transferPlayer(handler.player, assignment));
            PendingSessionJoinManager.getInstance().recordIfNeeded(handler.player, server);
        });

        ServerTickEvents.END_SERVER_TICK.register(SessionRoutingEvents::onServerTick);
    }

    private static void grantDevSessionOperator(ServerPlayerEntity player, MinecraftServer server) {
        if (!SessionPermissions.isDevBypassEnabled()) {
            return;
        }

        if (server.getPlayerManager().isOperator(player.getGameProfile())) {
            return;
        }

        server.getPlayerManager().addToOperators(player.getGameProfile());
        Miniverse.LOGGER.info("Granted dev bypass operator access to {} ({})", player.getName().getString(), player.getUuidAsString());
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
            notifyBackendAdminsOfPendingJoiners(server, sessionId);
            applySessionPauseFlag(sessionId);
            if (SessionRegistry.isSeedChangeRequested(sessionId)) {
                handleSessionServerSeedChange(server, sessionId);
                return;
            }
            if (!SessionRegistry.isStopRequested(sessionId)) {
                pendingReturnTransfers.remove(sessionId);
                return;
            }
            if (SessionRegistry.isReturnComplete(sessionId)) {
                pendingReturnTransfers.remove(sessionId);
                return;
            }

            String returnHost = SessionRuntimeConfig.getReturnHost();
            int returnPort = SessionRuntimeConfig.getReturnPort();
            if (!isHubAcceptingConnections(returnHost, returnPort)) {
                return;
            }

            MinigameManager minigameManager = MinigameManager.getInstance();
            if (minigameManager.getCurrentState() == GameState.RETURNING || minigameManager.getCurrentState() == GameState.FINISHED) {
                // MatchLifecycleController is already handling the return sequence
                return;
            }

            SessionManager sessionManager = SessionManager.getInstance();
            ReturnTransferState returnState = pendingReturnTransfers.computeIfAbsent(sessionId, ignored -> new ReturnTransferState());
            if (!returnState.transfersSent) {
                List<ServerPlayerEntity> players = playersToReturn(minigameManager, server);
                Miniverse.LOGGER.info("Session server {} received stop request; returning players to {}:{}", sessionId, returnHost, returnPort);
                if (players.isEmpty()) {
                    minigameManager.reset();
                    SessionRegistry.markReturnComplete(sessionId);
                    pendingReturnTransfers.remove(sessionId);
                    return;
                }

                returnState.transfersSent = true;
                returnState.startedAt = System.currentTimeMillis();
                returnState.pending.clear();
                for (ServerPlayerEntity player : players) {
                    returnState.pending.add(player.getUuid());
                    Miniverse.LOGGER.info("Returning player {} ({}) to {}:{}", player.getName().getString(), player.getUuidAsString(), returnHost, returnPort);
                    new dev.frost.miniverse.session.PlayerTransferService().transferPlayer(player, returnHost, returnPort);
                }
                return;
            }

            returnState.pending.removeIf(uuid -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                return player == null || player.isDisconnected();
            });

            if (returnState.pending.isEmpty()) {
                minigameManager.reset();
                SessionRegistry.markReturnComplete(sessionId);
                pendingReturnTransfers.remove(sessionId);
                return;
            }

            if (System.currentTimeMillis() - returnState.startedAt >= RETURN_CONFIRM_TIMEOUT_MS) {
                Miniverse.LOGGER.warn("Session server {} return timeout; forcing return complete with {} player(s) still connected.", sessionId, returnState.pending.size());
                minigameManager.reset();
                SessionRegistry.markReturnComplete(sessionId);
                pendingReturnTransfers.remove(sessionId);
            }
        });
    }

    private static void applySessionPauseFlag(String sessionId) {
        MinigameManager minigameManager = MinigameManager.getInstance();
        GameState currentState = minigameManager.getCurrentState();
        if (currentState == null) {
            return;
        }

        boolean pauseRequested = SessionRegistry.isPauseRequested(sessionId);
        if (pauseRequested && currentState != GameState.PAUSED) {
            if (minigameManager.pauseActiveGame()) {
                Miniverse.LOGGER.info("Paused session {} from registry lifecycle request.", sessionId);
            }
            return;
        }

        if (!pauseRequested && currentState == GameState.PAUSED) {
            if (minigameManager.resumeActiveGame()) {
                Miniverse.LOGGER.info("Resumed session {} from registry lifecycle request.", sessionId);
            }
        }
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
        handleMissedSessionTransfers(server, sessionManager);
        handleMidGameAssignmentRequests(server, sessionManager);

        List<SessionRegistry.StopState> stopStates = SessionRegistry.loadStopStates();
        if (stopStates.isEmpty()) {
            return;
        }

        for (SessionRegistry.StopState stopState : stopStates) {
            GameSession session = sessionManager.getSession(stopState.sessionId()).orElse(null);
            if (session == null) {
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

    private static void handleMissedSessionTransfers(MinecraftServer server, SessionManager sessionManager) {
        long now = System.currentTimeMillis();
        for (GameSession session : sessionManager.getSessions()) {
            if (session.getState() != SessionState.RUNNING) {
                continue;
            }
            if (SessionRegistry.isStopRequested(session.getSessionId())
                || SessionRegistry.isReturnComplete(session.getSessionId())) {
                clearRetryStateForSession(session.getSessionId());
                continue;
            }

            Instant launchedAt = session.getLaunchedAt();
            if (launchedAt == null || Duration.between(launchedAt, Instant.now()).toMillis() < MISSED_TRANSFER_GRACE_MS) {
                continue;
            }

            for (SessionGroup group : session.snapshotGroups()) {
                if (group.getState() != SessionState.RUNNING || group.getPort() == null) {
                    continue;
                }

                for (UUID playerUuid : group.getPlayerUuids()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                    TransferRetryKey key = new TransferRetryKey(session.getSessionId(), group.getGroupLabel(), playerUuid);
                    if (player == null || player.isDisconnected()) {
                        lastMissedTransferAttemptMs.remove(key);
                        continue;
                    }

                    Long lastAttempt = lastMissedTransferAttemptMs.get(key);
                    if (lastAttempt != null && now - lastAttempt < MISSED_TRANSFER_RETRY_MS) {
                        continue;
                    }

                    lastMissedTransferAttemptMs.put(key, now);
                    Miniverse.LOGGER.warn(
                        "Player {} ({}) is still on the lobby server {}s after session {} group {} became RUNNING; retrying transfer.",
                        player.getName().getString(),
                        playerUuid,
                        Duration.between(launchedAt, Instant.now()).toSeconds(),
                        session.getSessionId(),
                        group.getGroupLabel()
                    );
                    new dev.frost.miniverse.session.PlayerTransferService().transferPlayer(player, group);
                }
            }
        }
    }

    private static void clearRetryStateForSession(String sessionId) {
        lastMissedTransferAttemptMs.keySet().removeIf(key -> key.sessionId().equals(sessionId));
    }

    private static void notifyBackendAdminsOfPendingJoiners(MinecraftServer server, String sessionId) {
        long now = System.currentTimeMillis();
        if (now < nextBackendNoticePollMs) {
            return;
        }
        nextBackendNoticePollMs = now + BACKEND_NOTICE_POLL_MS;

        List<SessionRegistry.PendingJoinNotice> notices = SessionRegistry.listPendingJoinNotices(sessionId);
        if (notices.isEmpty()) {
            deliveredPendingJoinNotices.clear();
            return;
        }

        for (ServerPlayerEntity admin : server.getPlayerManager().getPlayerList()) {
            if (!SessionPermissions.canManageSessions(admin)) {
                continue;
            }
            Set<String> delivered = deliveredPendingJoinNotices.computeIfAbsent(admin.getUuid(), ignored -> new HashSet<>());
            for (SessionRegistry.PendingJoinNotice notice : notices) {
                String key = sessionId + ":" + notice.playerId();
                if (!delivered.add(key)) {
                    continue;
                }
                Text message = Text.literal(notice.playerName() + " joined the main server unassigned. Open Miniverse session admin to add them to this live session.")
                    .formatted(Formatting.YELLOW);
                admin.sendMessage(message, false);
                admin.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);
            }
        }
    }

    private static void handleMidGameAssignmentRequests(MinecraftServer server, SessionManager sessionManager) {
        for (SessionRegistry.MidGameAssignmentRequest request : SessionRegistry.loadMidGameAssignmentRequests()) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(request.playerId());
            if (target == null) {
                continue;
            }

            try {
                SessionRegistry.removeMidGameAssignmentRequest(request.sessionId(), request.requestId());
                sessionManager.assignPlayerMidGameAsync(
                    request.sessionId(),
                    target,
                    request.teamLabel(),
                    request.role(),
                    server
                ).whenComplete((group, error) -> server.execute(() -> {
                    if (error != null) {
                        Miniverse.LOGGER.warn(
                            "Failed to process backend mid-game assignment request {} for session {}.",
                            request.requestId(),
                            request.sessionId(),
                            error
                        );
                        return;
                    }

                    PendingSessionJoinManager.getInstance().remove(request.playerId());
                    new dev.frost.miniverse.session.PlayerTransferService().transferPlayer(target, group);
                    Miniverse.LOGGER.info(
                        "Processed backend mid-game assignment request {} for {} into session {}.",
                        request.requestId(),
                        target.getName().getString(),
                        request.sessionId()
                    );
                }));
            } catch (RuntimeException e) {
                Miniverse.LOGGER.warn(
                    "Failed to process backend mid-game assignment request {} for session {}.",
                    request.requestId(),
                    request.sessionId(),
                    e
                );
            }
        }
    }

    private static class ReturnTransferState {
        private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
        private long startedAt;
        private boolean transfersSent;
    }

    private record TransferRetryKey(String sessionId, String groupLabel, UUID playerUuid) {
    }
}
