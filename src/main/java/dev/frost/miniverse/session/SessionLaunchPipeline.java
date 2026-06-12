package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameRegistry;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

public class SessionLaunchPipeline {
    private final SessionStore store;
    private final SessionProcessMonitor monitor;
    private final ServerLauncher launcher;

    public SessionLaunchPipeline(SessionStore store, SessionProcessMonitor monitor, ServerLauncher launcher) {
        this.store = store;
        this.monitor = monitor;
        this.launcher = launcher;
    }

    
    
        private void notifyPlayersOfFailure(GameSession session, MinecraftServer server, String errorMessage) {
            Text message = Text.literal("Session launch failed: " + errorMessage).formatted(Formatting.RED);
            for (SessionGroup group : session.snapshotGroups()) {
                for (UUID playerUuid : group.getPlayerUuids()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                    if (player != null) {
                        player.sendMessage(message, false);
                    }
                }
            }
        }

    
    
        public CompletableFuture<ServerLauncher.InspectionLaunchResult> launchInspectionAsync(String sessionId, ServerPlayerEntity viewer) {
            Path sessionRoot = dev.frost.miniverse.common.MiniversePaths.sessionsRoot().resolve(sessionId);
            if (!SessionRegistry.existingSessionIds().contains(sessionId)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown retained session: " + sessionId));
            }
    
            try {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        ServerLauncher.InspectionLaunchResult result = this.launcher.launchInspection(sessionId, sessionRoot, viewer);
                        this.monitor.addInspectionProcess(sessionId + ":" + result.port(), result.process());
                        return result;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, this.monitor.getLauncherExecutor());
            } catch (RejectedExecutionException e) {
                return CompletableFuture.failedFuture(new IllegalStateException("Cannot queue inspection launch: launcher capacity is full", e));
            }
        }

    
    
        public CompletableFuture<ServerLauncher.MapEditorLaunchResult> launchMapEditorAsync(String mapName, ServerPlayerEntity editor) {
            return this.launchMapEditorAsync(mapName, editor, false);
        }

    
    
        private CompletableFuture<ServerLauncher.MapEditorLaunchResult> launchMapEditorAsync(String mapRef, ServerPlayerEntity editor, boolean existingMap) {
            try {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        ServerLauncher.MapEditorLaunchResult result = existingMap
                            ? this.launcher.launchMapEditorForExistingMap(mapRef, editor)
                            : this.launcher.launchMapEditor(mapRef, editor);
                        this.monitor.addMapEditorProcess(result.mapId() + ":" + result.port(), result);
                        return result;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, this.monitor.getLauncherExecutor());
            } catch (RejectedExecutionException e) {
                return CompletableFuture.failedFuture(new IllegalStateException("Cannot queue map editor launch: launcher capacity is full", e));
            }
        }

    
    
        public CompletableFuture<ServerLauncher.MapEditorLaunchResult> launchExistingMapEditorAsync(String mapId, ServerPlayerEntity editor) {
            return this.launchMapEditorAsync(mapId, editor, true);
        }

    
    
        public CompletableFuture<GameSession> changeSeedAndRelaunchSession(String sessionId, MinecraftServer server) {
            SeedPlan seedPlan = SeedPlan.randomSameSeed();
            synchronized (this) {
                GameSession session = this.store.getSession(sessionId).orElse(null);
                if (session == null) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown session: " + sessionId));
                }
                if (session.isEmpty()) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Session has no assigned players: " + sessionId));
                }
    
                session.setState(SessionState.STOPPING);
                this.monitor.stopBackendProcesses(session);
                session.setSeedPlan(seedPlan);
                session.setState(SessionState.CREATED);
                SessionRegistry.clearStopRequested(sessionId);
                SessionRegistry.clearReturnComplete(sessionId);
                SessionRegistry.clearSeedChangeRequested(sessionId);
                this.store.persistRegistry();
                Miniverse.LOGGER.info("Changing seed for session {} to {}", sessionId, seedPlan.sharedSeed());
            }
    
            return this.launchSession(sessionId, server);
        }

    
    
        public CompletableFuture<GameSession> stageSeedChangeRelaunch(String sessionId, MinecraftServer server) {
            SeedPlan seedPlan = SeedPlan.randomSameSeed();
            SeedPlan previousSeedPlan;
            GameSession session;
            List<SessionGroup> groups;
            synchronized (this) {
                session = this.store.getSession(sessionId).orElse(null);
                if (session == null) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown session: " + sessionId));
                }
                if (session.isEmpty()) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Session has no assigned players: " + sessionId));
                }
                if (session.getState() == SessionState.LAUNCHING) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Seed change is already staging for " + sessionId));
                }
    
                groups = new ArrayList<>(session.snapshotGroups());
                previousSeedPlan = session.getSeedPlan();
                this.monitor.addPendingSeedChangeStops(sessionId, pendingStopsFor(session, groups));
                SessionRegistry.clearSeedChangeTargets(sessionId);
                SessionRegistry.clearReturnComplete(sessionId);
                session.setSeedPlan(seedPlan);
                session.setState(SessionState.LAUNCHING);
                this.store.persistRegistry();
                Miniverse.LOGGER.info("Staging new seed for session {}: {}", sessionId, seedPlan.sharedSeed());
            }
    
            SessionOperatorSnapshot operatorSnapshot = SessionOperatorSnapshot.capture(server, groups);
            String directorySuffix = "seed_" + Long.toUnsignedString(seedPlan.sharedSeed()) + "_" + System.currentTimeMillis();
    
            if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD) {
                SessionGroup originalPrimary = groups.getFirst();
                SessionGroup replacementPrimary = replacementGroup(session, originalPrimary, seedPlan);
                return this.launchReplacementGroupAsync(session, replacementPrimary, operatorSnapshot.forGroups(groups), directorySuffix)
                    .thenApply(launchedGroup -> {
                        synchronized (this) {
                            BackendInstance backend = launchedGroup.getBackendInstance();
                            originalPrimary.attachBackend(backend);
                            for (int i = 1; i < groups.size(); i++) {
                                groups.get(i).attachBackend(backend);
                            }
                            session.setState(SessionState.RUNNING);
                            this.store.persistRegistry();
                            SessionRegistry.setSeedChangeTarget(sessionId, originalPrimary.getGroupLabel(), seedChangeTargetHost(), seedChangeTargetPort(launchedGroup.getPort()));
                        }
                        return session;
                    })
                    .exceptionally(error -> {
                        synchronized (this) {
                            session.setSeedPlan(previousSeedPlan);
                            session.setState(SessionState.RUNNING);
                            this.monitor.removePendingSeedChangeStops(sessionId);
                            SessionRegistry.clearStopRequested(sessionId);
                            SessionRegistry.clearSeedChangeRequested(sessionId);
                            SessionRegistry.clearSeedChangeTargets(sessionId);
                            this.store.persistRegistry();
                        }
                        this.notifyPlayersOfFailure(session, server, error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                        throw new RuntimeException(error);
                    });
            }
    
            Map<String, SessionGroup> replacements = new LinkedHashMap<>();
            List<CompletableFuture<SessionGroup>> launches = new ArrayList<>();
            for (SessionGroup group : groups) {
                SessionGroup replacement = replacementGroup(session, group, seedPlan);
                replacements.put(group.getGroupLabel(), replacement);
                launches.add(this.launchReplacementGroupAsync(session, replacement, operatorSnapshot.forGroups(List.of(group)), directorySuffix));
            }
    
            return CompletableFuture.allOf(launches.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    synchronized (this) {
                        for (SessionGroup group : groups) {
                            SessionGroup replacement = replacements.get(group.getGroupLabel());
                            group.attachBackend(replacement.getBackendInstance());
                            SessionRegistry.setSeedChangeTarget(sessionId, group.getGroupLabel(), seedChangeTargetHost(), seedChangeTargetPort(replacement.getPort()));
                        }
                        session.setState(SessionState.RUNNING);
                        this.store.persistRegistry();
                    }
                    return session;
                })
                .exceptionally(error -> {
                    synchronized (this) {
                        session.setSeedPlan(previousSeedPlan);
                        session.setState(SessionState.RUNNING);
                        this.monitor.removePendingSeedChangeStops(sessionId);
                        SessionRegistry.clearStopRequested(sessionId);
                        SessionRegistry.clearSeedChangeRequested(sessionId);
                        SessionRegistry.clearSeedChangeTargets(sessionId);
                        this.store.persistRegistry();
                    }
                    this.notifyPlayersOfFailure(session, server, error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                    throw new RuntimeException(error);
                });
        }

    
    
        public synchronized void completeSeedChangeRelaunch(String sessionId) {
            List<SessionProcessMonitor.PendingBackendStop> pending = this.monitor.removePendingSeedChangeStops(sessionId);
            if (pending != null) {
                for (SessionProcessMonitor.PendingBackendStop backend : pending) {
                    this.monitor.stopProcess(backend.process());
                }
            }
            SessionRegistry.clearStopRequested(sessionId);
            SessionRegistry.clearReturnComplete(sessionId);
            SessionRegistry.clearSeedChangeRequested(sessionId);
            SessionRegistry.clearSeedChangeTargets(sessionId);
            this.store.persistRegistry();
        }

    
    
        public synchronized List<String> expectedSeedChangeCompletionGroups(String sessionId) {
            GameSession session = this.store.getSession(sessionId).orElse(null);
            if (session == null || session.isEmpty()) {
                return List.of();
            }
            List<SessionGroup> groups = new ArrayList<>(session.snapshotGroups());
            if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD) {
                return List.of(groups.getFirst().getGroupLabel());
            }
            return groups.stream().map(SessionGroup::getGroupLabel).toList();
        }

    
    
        private static List<SessionProcessMonitor.PendingBackendStop> pendingStopsFor(GameSession session, List<SessionGroup> groups) {
            if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD) {
                SessionGroup primary = groups.getFirst();
                return primary.getProcess() == null ? List.of() : List.of(new SessionProcessMonitor.PendingBackendStop(primary.getGroupLabel(), primary.getProcess()));
            }
            return groups.stream()
                .filter(group -> group.getProcess() != null)
                .map(group -> new SessionProcessMonitor.PendingBackendStop(group.getGroupLabel(), group.getProcess()))
                .toList();
        }

    
    
        private static SessionGroup replacementGroup(GameSession session, SessionGroup group, SeedPlan seedPlan) {
            return new SessionGroup(session.getSessionId(), session.getGameType(), seedPlan, group.getPlannedTeam());
        }

    
    
        private static int publicPort(Integer localPort) {
            if (localPort == null) {
                return -1;
            }
            return SessionLauncherConfig.getInstance().publicPortForLocalPort(localPort);
        }

    
    
        private static String seedChangeTargetHost() {
            return VelocityProxyConfig.getInstance().velocityEnabled()
                ? VelocityProxyConfig.getInstance().backendHost()
                : SessionServerConfig.getInstance().advertisedHost();
        }

    
    
        private static int seedChangeTargetPort(Integer localPort) {
            if (localPort == null) {
                return -1;
            }
            return VelocityProxyConfig.getInstance().velocityEnabled() ? localPort : publicPort(localPort);
        }

    
    
        public CompletableFuture<SessionGroup> launchGroupAsync(GameSession session, SessionGroup group) {
            return this.launchGroupAsync(session, group, SessionOperatorSnapshot.empty());
        }

    
    
        public CompletableFuture<SessionGroup> launchGroupAsync(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot) {
            return this.launchGroupAsync(session, group, operatorSnapshot, null);
        }

    
    
        public CompletableFuture<SessionGroup> launchGroupAsync(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, @Nullable MinecraftServer server) {
            try {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        if (server == null) {
                            return this.launcher.launch(session, group, operatorSnapshot).group();
                        }
                        return this.launcher.launch(session, group, operatorSnapshot, "", true, progress ->
                            LaunchProgressBroadcaster.broadcastLaunchProgress(server, session, progress.stage(), progress.detail(), progress.progress(), false)
                        ).group();
                    } catch (IOException e) {
                        group.markFailed(e.getMessage());
                        throw new RuntimeException(e);
                    }
                }, this.monitor.getLauncherExecutor());
            } catch (RejectedExecutionException e) {
                String message = this.launchCapacityMessage(session, group);
                group.markFailed(message);
                this.store.persistRegistry();
                Miniverse.LOGGER.warn(message);
                return CompletableFuture.failedFuture(new IllegalStateException(message, e));
            }
        }

    
    
        private CompletableFuture<SessionGroup> launchReplacementGroupAsync(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, String directorySuffix) {
            try {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return this.launcher.launch(session, group, operatorSnapshot, directorySuffix, false).group();
                    } catch (IOException e) {
                        group.markFailed(e.getMessage());
                        throw new RuntimeException(e);
                    }
                }, this.monitor.getLauncherExecutor());
            } catch (RejectedExecutionException e) {
                String message = this.launchCapacityMessage(session, group);
                group.markFailed(message);
                this.store.persistRegistry();
                Miniverse.LOGGER.warn(message);
                return CompletableFuture.failedFuture(new IllegalStateException(message, e));
            }
        }

    
    
        private CompletableFuture<SessionGroup> launchRetainedGroupAsync(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, Path retainedRoot, MinecraftServer server) {
            try {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return this.launcher.launchFromRetained(session, group, operatorSnapshot, retainedRoot, true, progress ->
                            LaunchProgressBroadcaster.broadcastLaunchProgress(server, session, progress.stage(), progress.detail(), progress.progress(), false)
                        ).group();
                    } catch (IOException e) {
                        group.markFailed(e.getMessage());
                        throw new RuntimeException(e);
                    }
                }, this.monitor.getLauncherExecutor());
            } catch (RejectedExecutionException e) {
                String message = this.launchCapacityMessage(session, group);
                group.markFailed(message);
                this.store.persistRegistry();
                Miniverse.LOGGER.warn(message);
                return CompletableFuture.failedFuture(new IllegalStateException(message, e));
            }
        }

    
    
    private String launchCapacityMessage(GameSession session, SessionGroup group) {
        return "Cannot queue backend launch for " + group.getDisplayName()
            + " in session " + session.getSessionId()
            + ": launcher capacity is full"
            + " (active=" + this.monitor.getLauncherExecutor().getActiveCount()
            + ", queued=" + this.monitor.getLauncherExecutor().getQueue().size()
            + ", maxConcurrent=" + this.monitor.getLauncherExecutor().getMaximumPoolSize()
            + ", queueCapacity=" + (this.monitor.getLauncherExecutor().getQueue().size() + this.monitor.getLauncherExecutor().getQueue().remainingCapacity())
            + "). Increase config/miniverse/session-launcher.json or wait for pending launches to finish.";
    }



    public CompletableFuture<GameSession> relaunchRetainedSession(String sessionId, MinecraftServer server) {
        SessionRegistry.RetainedSession retained = SessionRegistry.loadRetainedSession(sessionId).orElse(null);
        if (retained == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown retained session: " + sessionId));
        }

        Optional<MinigameDefinition> definition = MinigameRegistry.get(retained.gameId());
        if (definition.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unknown game type '" + retained.gameId() + "' for retained session " + sessionId));
        }

        if (retained.teams().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Retained session " + sessionId + " has no player roster."));
        }

        GameSession session;
        synchronized (this) {
            if (this.store.getSession(sessionId).isPresent()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Session is already running: " + sessionId));
            }
            SessionGameDescriptor gameType = SessionGameDescriptor.fromDefinition(definition.get());
            session = new GameSession(sessionId, gameType, retained.seedPlan());
            session.setSettings(retained.settings());
            this.store.putSession(session);
            SessionRegistry.clearStopRequested(sessionId);
            SessionRegistry.clearReturnComplete(sessionId);
            SessionRegistry.clearSeedChangeRequested(sessionId);
            session.setState(SessionState.LAUNCHING);
            this.store.persistRegistry();
        }

        for (PlannedTeam team : retained.teams()) {
            this.store.createGroup(sessionId, team);
        }

        Path retainedRoot = MiniversePaths.sessionsRoot().resolve(sessionId);
        List<SessionGroup> groups = new ArrayList<>(session.snapshotGroups());
        SessionOperatorSnapshot operatorSnapshot = SessionOperatorSnapshot.capture(server, groups);

        if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD && !groups.isEmpty()) {
            SessionGroup primary = groups.get(0);
            CompletableFuture<SessionGroup> primaryLaunch = this.launchRetainedGroupAsync(session, primary, operatorSnapshot.forGroups(groups), retainedRoot, server);
            return primaryLaunch.thenApply(launchedGroup -> {
                BackendInstance backend = launchedGroup.getBackendInstance();
                for (int i = 1; i < groups.size(); i++) {
                    groups.get(i).attachBackend(backend);
                }
                session.setState(SessionState.RUNNING);
                this.store.persistRegistry();
                return session;
            }).exceptionally(error -> {
                session.setState(SessionState.FAILED);
                this.store.persistRegistry();
                this.notifyPlayersOfFailure(session, server, error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                throw new RuntimeException(error);
            });
        }

        List<CompletableFuture<SessionGroup>> launches = new ArrayList<>();
        for (SessionGroup group : groups) {
            launches.add(this.launchRetainedGroupAsync(session, group, operatorSnapshot.forGroups(List.of(group)), retainedRoot, server));
        }

        return CompletableFuture.allOf(launches.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                session.setState(SessionState.RUNNING);
                this.store.persistRegistry();
                return session;
            })
            .exceptionally(error -> {
                session.setState(SessionState.FAILED);
                this.store.persistRegistry();
                this.notifyPlayersOfFailure(session, server, error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                throw new RuntimeException(error);
            });
    }



    public CompletableFuture<GameSession> launchSession(String sessionId, MinecraftServer server) {
        GameSession session;
        synchronized (this) {
            session = this.store.getSession(sessionId).orElse(null);
        }

        if (session == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown session: " + sessionId));
        }

        if (session.getState() == SessionState.LAUNCHING || session.getState() == SessionState.RUNNING) {
            return CompletableFuture.failedFuture(new IllegalStateException("Session is already launching or running: " + sessionId));
        }

        if (session.getState() == SessionState.STOPPING || session.getState() == SessionState.STOPPED) {
            return CompletableFuture.failedFuture(new IllegalStateException("Session is stopping or stopped: " + sessionId));
        }

        if (SessionRegistry.isStopRequested(sessionId) && !SessionRegistry.isReturnComplete(sessionId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Session is still stopping: return not complete for " + sessionId));
        }

        if (session.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Session has no assigned players: " + sessionId));
        }

        session.setState(SessionState.LAUNCHING);

        List<SessionGroup> groups = new ArrayList<>(session.snapshotGroups());
        SessionOperatorSnapshot operatorSnapshot = SessionOperatorSnapshot.capture(server, groups);

        if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD && groups.size() > 0) {
            SessionGroup primary = groups.get(0);
            CompletableFuture<SessionGroup> primaryLaunch = this.launchGroupAsync(session, primary, operatorSnapshot.forGroups(groups), server);

            return primaryLaunch.thenApply(launchedGroup -> {
                BackendInstance backend = launchedGroup.getBackendInstance();

                for (int i = 1; i < groups.size(); i++) {
                    SessionGroup other = groups.get(i);
                    other.attachBackend(backend);
                }

                session.setState(SessionState.RUNNING);
                this.store.persistRegistry();
                return session;
            }).exceptionally(error -> {
                session.setState(SessionState.FAILED);
                this.store.persistRegistry();
                this.notifyPlayersOfFailure(session, server, error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                throw new RuntimeException(error);
            });
        }

        List<CompletableFuture<SessionGroup>> launches = new ArrayList<>();
        for (SessionGroup group : groups) {
            launches.add(this.launchGroupAsync(session, group, operatorSnapshot.forGroups(List.of(group)), server));
        }

        return CompletableFuture.allOf(launches.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                session.setState(SessionState.RUNNING);
                this.store.persistRegistry();
                return session;
            })
            .exceptionally(error -> {
                session.setState(SessionState.FAILED);
                this.store.persistRegistry();
                this.notifyPlayersOfFailure(session, server, error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                throw new RuntimeException(error);
            });
    }

}
