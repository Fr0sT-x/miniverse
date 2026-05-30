package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameRegistry;
import dev.frost.miniverse.network.TransitionTransferCoordinator;
import dev.frost.miniverse.network.SessionNetwork;
import dev.frost.miniverse.network.VelocityProxyBridge;
import dev.frost.miniverse.common.MiniversePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();

    private final Map<String, GameSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, String> playerSessions = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sessionCounters = new ConcurrentHashMap<>();
    private final Map<String, List<PendingBackendStop>> pendingSeedChangeStops = new ConcurrentHashMap<>();
    private final Map<String, Process> inspectionProcesses = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor launcherExecutor = this.createLauncherExecutor();
    private final ServerLauncher serverLauncher = new ServerLauncher();

    private record PendingBackendStop(String groupLabel, Process process) {
    }

    private SessionManager() {
        this.registerShutdownHook();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Miniverse.LOGGER.info("Cleaning up backend session servers...");
            synchronized (this) {
                for (GameSession session : this.sessions.values()) {
                    for (SessionGroup group : session.snapshotGroups()) {
                        if (group.isAlive()) {
                            Miniverse.LOGGER.info("Terminating backend process for {} in session {}", group.getDisplayName(), session.getSessionId());
                            this.serverLauncher.stop(group);
                        }
                    }
                }
                for (Map.Entry<String, Process> entry : this.inspectionProcesses.entrySet()) {
                    Miniverse.LOGGER.info("Terminating inspection backend {}", entry.getKey());
                    stopProcess(entry.getValue());
                }
                this.inspectionProcesses.clear();
            }
            this.launcherExecutor.shutdownNow();
            Miniverse.LOGGER.info("Backend session server cleanup complete.");
        }, "Miniverse-ShutdownHook"));
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public synchronized GameSession createSession(SessionGameDescriptor gameType) {
        return this.createSession(gameType, SeedPlan.randomSameSeed());
    }

    public synchronized GameSession createSession(SessionGameDescriptor gameType, SeedPlan seedPlan) {
        String sessionId = this.nextSessionId(gameType);
        GameSession session = new GameSession(sessionId, gameType, seedPlan);
        this.sessions.put(sessionId, session);
        SessionRegistry.clearStopRequested(sessionId);
        SessionRegistry.clearReturnComplete(sessionId);
        this.persistRegistry();
        Miniverse.LOGGER.info("Created {} session {} with seed {}", gameType.getDisplayName(), sessionId, session.getSeedPlan().sharedSeed());
        return session;
    }

    public synchronized GameSession createSession(SessionGameDescriptor gameType, Collection<ServerPlayerEntity> players) {
        GameSession session = this.createSession(gameType);
        for (ServerPlayerEntity player : players) {
            this.assignPlayer(session.getSessionId(), player);
        }
        return session;
    }

    public synchronized SessionGroup createGroup(String sessionId, String label, Collection<ServerPlayerEntity> players) {
        return this.createGroup(sessionId, new PlannedTeam(label, players.stream().map(SessionMembership::of).toList()));
    }

    public synchronized SessionGroup createGroup(String sessionId, PlannedTeam plannedTeam) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown session: " + sessionId);
        }

        for (SessionMembership member : plannedTeam.members()) {
            this.playerSessions.put(member.playerUuid(), sessionId);
        }

        SessionGroup group = session.addGroup(plannedTeam);
        this.persistRegistry();
        return group;
    }

    public synchronized SessionGroup assignPlayers(String sessionId, String label, Collection<ServerPlayerEntity> players) {
        return this.createGroup(sessionId, label, players);
    }

    public synchronized List<GameSession> getSessions() {
        return Collections.unmodifiableList(new ArrayList<>(this.sessions.values()));
    }

    public synchronized Optional<GameSession> getSession(String sessionId) {
        return Optional.ofNullable(this.sessions.get(sessionId));
    }

    public Optional<GameSession> getSessionForPlayer(UUID playerUuid) {
        String sessionId = this.playerSessions.get(playerUuid);
        if (sessionId == null) {
            return Optional.empty();
        }
        return this.getSession(sessionId);
    }

    public synchronized SessionGroup assignPlayer(String sessionId, ServerPlayerEntity player) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown session: " + sessionId);
        }

        String previousSessionId = this.playerSessions.put(player.getUuid(), sessionId);
        if (previousSessionId != null && !previousSessionId.equals(sessionId)) {
            GameSession previousSession = this.sessions.get(previousSessionId);
            if (previousSession != null) {
                previousSession.removePlayer(player.getUuid());
            }
        }

        SessionGroup group = session.addPlayer(player.getUuid(), player.getName().getString());
        Miniverse.LOGGER.info("Assigned player {} to {} session {}", player.getName().getString(), session.getGameType().getDisplayName(), sessionId);

        if (session.getState() == SessionState.RUNNING || session.getState() == SessionState.LAUNCHING) {
            this.launchGroupAsync(session, group);
        }

        this.persistRegistry();

        return group;
    }

    public synchronized SessionGroup assignPlayerMidGame(String sessionId, ServerPlayerEntity player, String teamLabel, String role) {
        return this.assignPlayerMidGameInternal(sessionId, player, teamLabel, role);
    }

    public CompletableFuture<SessionGroup> assignPlayerMidGameAsync(String sessionId, ServerPlayerEntity player, String teamLabel, String role, MinecraftServer server) {
        SessionGroup group;
        boolean launchNewBackend;
        synchronized (this) {
            group = this.assignPlayerMidGameInternal(sessionId, player, teamLabel, role);
            GameSession session = this.sessions.get(sessionId);
            launchNewBackend = session != null
                && session.getGameType().getTopology() == SessionTopology.ISOLATED_WORLD
                && group.getState() != SessionState.RUNNING;
            if (!launchNewBackend) {
                return CompletableFuture.completedFuture(group);
            }
        }

        GameSession session;
        synchronized (this) {
            session = this.sessions.get(sessionId);
        }
        if (session == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown session: " + sessionId));
        }

        SessionOperatorSnapshot operatorSnapshot = SessionOperatorSnapshot.capture(server, List.of(group));
        return this.launchGroupAsync(session, group, operatorSnapshot, server)
            .thenApply(launchedGroup -> {
                synchronized (this) {
                    this.persistRegistry();
                }
                return launchedGroup;
            });
    }

    private SessionGroup assignPlayerMidGameInternal(String sessionId, ServerPlayerEntity player, String teamLabel, String role) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown session: " + sessionId);
        }
        if (session.getState() != SessionState.RUNNING) {
            throw new IllegalStateException("Session is not running: " + sessionId);
        }

        String resolvedRole = this.resolveLateJoinRole(session, role);
        String resolvedTeam = this.resolveLateJoinTeam(session, teamLabel, resolvedRole);
        boolean existingGroup = session.snapshotGroups().stream()
            .anyMatch(group -> group.getGroupLabel().equalsIgnoreCase(resolvedTeam));

        SessionMembership membership = new SessionMembership(player.getUuid(), player.getName().getString(), resolvedRole);
        String previousSessionId = this.playerSessions.get(player.getUuid());
        if (previousSessionId != null && !previousSessionId.equals(sessionId)) {
            GameSession previousSession = this.sessions.get(previousSessionId);
            if (previousSession != null) {
                previousSession.removePlayer(player.getUuid());
            }
        }
        SessionGroup group = session.addMemberToGroup(resolvedTeam, membership);
        if (group.getState() != SessionState.RUNNING || group.getPort() == null) {
            if (session.getGameType().getTopology() != SessionTopology.ISOLATED_WORLD || existingGroup) {
                SessionGroup primary = session.snapshotGroups().stream()
                    .filter(candidate -> candidate != group)
                    .filter(candidate -> candidate.getState() == SessionState.RUNNING && candidate.getPort() != null)
                    .findFirst()
                    .orElse(null);
                if (primary == null) {
                    throw new IllegalStateException("No running backend is available for late player assignment.");
                }
                group.attachBackend(primary.getBackendInstance());
            }
        }

        this.playerSessions.put(player.getUuid(), sessionId);
        this.persistRegistry();
        Miniverse.LOGGER.info(
            "Assigned late-joining player {} to {} session {} as {} in {}.",
            player.getName().getString(),
            session.getGameType().getDisplayName(),
            sessionId,
            resolvedRole.isBlank() ? "member" : resolvedRole,
            resolvedTeam
        );
        return group;
    }

    private String resolveLateJoinRole(GameSession session, String role) {
        String resolvedRole = role == null ? "" : role.trim();
        if (!isManhunt(session)) {
            return resolvedRole;
        }
        return switch (resolvedRole.toLowerCase(Locale.ROOT)) {
            case "speedrunner", "runner" -> "speedrunner";
            case "hunter" -> "hunter";
            default -> throw new IllegalArgumentException("Manhunt late joins require an explicit role: Speedrunner or Hunter.");
        };
    }

    private String resolveLateJoinTeam(GameSession session, String teamLabel, String role) {
        if (isManhunt(session)) {
            return switch (role.toLowerCase(Locale.ROOT)) {
                case "speedrunner" -> "Speedrunners";
                case "hunter" -> "Hunters";
                default -> throw new IllegalArgumentException("Manhunt late joins require an explicit role: Speedrunner or Hunter.");
            };
        }
        if (teamLabel != null && !teamLabel.isBlank()) {
            return teamLabel.trim();
        }
        if (session.getGameType().getTopology() == SessionTopology.ISOLATED_WORLD) {
            return session.snapshotGroups().stream()
                .filter(group -> group.getState() == SessionState.RUNNING && group.getPort() != null)
                .map(SessionGroup::getGroupLabel)
                .findFirst()
                .orElse(session.getGameType().getDisplayName());
        }
        return session.getGameType().getDisplayName();
    }

    private static boolean isManhunt(GameSession session) {
        return session != null && "manhunt".equalsIgnoreCase(session.getGameType().getCommandName());
    }

    public synchronized @Nullable SessionGroup unassignPlayer(UUID playerUuid) {
        String sessionId = this.playerSessions.remove(playerUuid);
        if (sessionId == null) {
            return null;
        }

        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            return null;
        }

        SessionGroup removed = session.removePlayer(playerUuid);
        this.persistRegistry();
        return removed;
    }

    public synchronized void clearPlayerSessionsForSession(String sessionId) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            return;
        }

        for (SessionGroup group : session.snapshotGroups()) {
            for (UUID playerUuid : group.getPlayerUuids()) {
                this.playerSessions.remove(playerUuid, sessionId);
            }
        }
    }

    public synchronized void clearSessionGroupsForSession(String sessionId) {
        this.clearPlayerSessionsForSession(sessionId);
    }

    public CompletableFuture<GameSession> launchSession(String sessionId, MinecraftServer server) {
        GameSession session;
        synchronized (this) {
            session = this.sessions.get(sessionId);
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
                this.persistRegistry();
                return session;
            }).exceptionally(error -> {
                session.setState(SessionState.FAILED);
                this.persistRegistry();
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
                this.persistRegistry();
                return session;
            })
            .exceptionally(error -> {
                session.setState(SessionState.FAILED);
                this.persistRegistry();
                this.notifyPlayersOfFailure(session, server, error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                throw new RuntimeException(error);
            });
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
            if (this.sessions.containsKey(sessionId)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Session is already running: " + sessionId));
            }
            SessionGameDescriptor gameType = SessionGameDescriptor.fromDefinition(definition.get());
            session = new GameSession(sessionId, gameType, retained.seedPlan());
            session.setSettings(retained.settings());
            this.sessions.put(sessionId, session);
            SessionRegistry.clearStopRequested(sessionId);
            SessionRegistry.clearReturnComplete(sessionId);
            SessionRegistry.clearSeedChangeRequested(sessionId);
            session.setState(SessionState.LAUNCHING);
            this.persistRegistry();
            SessionRegistry.clearStopRequested(sessionId);
            SessionRegistry.clearReturnComplete(sessionId);
            SessionRegistry.clearSeedChangeRequested(sessionId);
        }

        for (PlannedTeam team : retained.teams()) {
            this.createGroup(sessionId, team);
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
                this.persistRegistry();
                return session;
            }).exceptionally(error -> {
                session.setState(SessionState.FAILED);
                this.persistRegistry();
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
                this.persistRegistry();
                return session;
            })
            .exceptionally(error -> {
                session.setState(SessionState.FAILED);
                this.persistRegistry();
                this.notifyPlayersOfFailure(session, server, error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                throw new RuntimeException(error);
            });
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

    public synchronized void stopSession(String sessionId) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            return;
        }

        session.setState(SessionState.STOPPING);
        this.stopBackendProcesses(session);
        session.setState(SessionState.STOPPED);
        this.persistRegistry();
    }

    public synchronized void reapDeadBackends(MinecraftServer server) {
        boolean changed = false;
        for (GameSession session : this.sessions.values()) {
            if (session.getState() != SessionState.RUNNING && session.getState() != SessionState.LAUNCHING) {
                continue;
            }

            List<SessionGroup> deadGroups = session.snapshotGroups().stream()
                .filter(group -> group.getState() == SessionState.RUNNING || group.getState() == SessionState.LAUNCHING)
                .filter(group -> group.getProcess() != null && !group.getProcess().isAlive())
                .toList();
            if (deadGroups.isEmpty()) {
                continue;
            }

            session.setState(SessionState.FAILED);
            for (SessionGroup group : deadGroups) {
                int exitCode = exitCode(group.getProcess());
                String error = "Backend process exited unexpectedly" + (exitCode == Integer.MIN_VALUE ? "" : " with exit code " + exitCode);
                group.markFailed(error);
                Miniverse.LOGGER.warn(
                    "{} session {} backend {} is no longer alive: {}.",
                    session.getGameType().getDisplayName(),
                    session.getSessionId(),
                    group.getDisplayName(),
                    error
                );
            }

            Text message = Text.literal("Session backend stopped unexpectedly. Please create or launch a new session.").formatted(Formatting.RED);
            for (SessionGroup group : session.snapshotGroups()) {
                for (UUID playerUuid : group.getPlayerUuids()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                    if (player != null) {
                        player.sendMessage(message, false);
                    }
                    this.playerSessions.remove(playerUuid, session.getSessionId());
                }
            }
            changed = true;
        }

        if (changed) {
            this.persistRegistry();
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
                    ServerLauncher.InspectionLaunchResult result = this.serverLauncher.launchInspection(sessionId, sessionRoot, viewer);
                    this.inspectionProcesses.put(sessionId + ":" + result.port(), result.process());
                    return result;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, this.launcherExecutor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cannot queue inspection launch: launcher capacity is full", e));
        }
    }

    public CompletableFuture<GameSession> changeSeedAndRelaunchSession(String sessionId, MinecraftServer server) {
        SeedPlan seedPlan = SeedPlan.randomSameSeed();
        synchronized (this) {
            GameSession session = this.sessions.get(sessionId);
            if (session == null) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown session: " + sessionId));
            }
            if (session.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Session has no assigned players: " + sessionId));
            }

            session.setState(SessionState.STOPPING);
            this.stopBackendProcesses(session);
            session.setSeedPlan(seedPlan);
            session.setState(SessionState.CREATED);
            SessionRegistry.clearStopRequested(sessionId);
            SessionRegistry.clearReturnComplete(sessionId);
            SessionRegistry.clearSeedChangeRequested(sessionId);
            this.persistRegistry();
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
            session = this.sessions.get(sessionId);
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
            this.pendingSeedChangeStops.put(sessionId, pendingStopsFor(session, groups));
            SessionRegistry.clearSeedChangeTargets(sessionId);
            SessionRegistry.clearReturnComplete(sessionId);
            session.setSeedPlan(seedPlan);
            session.setState(SessionState.LAUNCHING);
            this.persistRegistry();
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
                        this.persistRegistry();
                        SessionRegistry.setSeedChangeTarget(sessionId, originalPrimary.getGroupLabel(), seedChangeTargetHost(), seedChangeTargetPort(launchedGroup.getPort()));
                    }
                    return session;
                })
                .exceptionally(error -> {
                    synchronized (this) {
                        session.setSeedPlan(previousSeedPlan);
                        session.setState(SessionState.RUNNING);
                        this.pendingSeedChangeStops.remove(sessionId);
                        SessionRegistry.clearStopRequested(sessionId);
                        SessionRegistry.clearSeedChangeRequested(sessionId);
                        SessionRegistry.clearSeedChangeTargets(sessionId);
                        this.persistRegistry();
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
                    this.persistRegistry();
                }
                return session;
            })
            .exceptionally(error -> {
                synchronized (this) {
                    session.setSeedPlan(previousSeedPlan);
                    session.setState(SessionState.RUNNING);
                    this.pendingSeedChangeStops.remove(sessionId);
                    SessionRegistry.clearStopRequested(sessionId);
                    SessionRegistry.clearSeedChangeRequested(sessionId);
                    SessionRegistry.clearSeedChangeTargets(sessionId);
                    this.persistRegistry();
                }
                this.notifyPlayersOfFailure(session, server, error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                throw new RuntimeException(error);
            });
    }

    public synchronized void completeSeedChangeRelaunch(String sessionId) {
        List<PendingBackendStop> pending = this.pendingSeedChangeStops.remove(sessionId);
        if (pending != null) {
            for (PendingBackendStop backend : pending) {
                stopProcess(backend.process());
            }
        }
        SessionRegistry.clearStopRequested(sessionId);
        SessionRegistry.clearReturnComplete(sessionId);
        SessionRegistry.clearSeedChangeRequested(sessionId);
        SessionRegistry.clearSeedChangeTargets(sessionId);
        this.persistRegistry();
    }

    public synchronized List<String> expectedSeedChangeCompletionGroups(String sessionId) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null || session.isEmpty()) {
            return List.of();
        }
        List<SessionGroup> groups = new ArrayList<>(session.snapshotGroups());
        if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD) {
            return List.of(groups.getFirst().getGroupLabel());
        }
        return groups.stream().map(SessionGroup::getGroupLabel).toList();
    }

    private void stopBackendProcesses(GameSession session) {
        for (SessionGroup group : session.snapshotGroups()) {
            this.serverLauncher.stop(group);
        }
    }

    private static List<PendingBackendStop> pendingStopsFor(GameSession session, List<SessionGroup> groups) {
        if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD) {
            SessionGroup primary = groups.getFirst();
            return primary.getProcess() == null ? List.of() : List.of(new PendingBackendStop(primary.getGroupLabel(), primary.getProcess()));
        }
        return groups.stream()
            .filter(group -> group.getProcess() != null)
            .map(group -> new PendingBackendStop(group.getGroupLabel(), group.getProcess()))
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

    private static void stopProcess(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static int exitCode(Process process) {
        if (process == null || process.isAlive()) {
            return Integer.MIN_VALUE;
        }
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    public synchronized void removeSession(String sessionId) {
        this.stopSession(sessionId);
        this.sessions.remove(sessionId);
        this.playerSessions.entrySet().removeIf(entry -> sessionId.equals(entry.getValue()));
        SessionRegistry.removeSession(sessionId);
        this.persistRegistry();
    }

    public synchronized void archiveSession(String sessionId) {
        GameSession session = this.sessions.get(sessionId);
        if (session != null) {
            session.setState(SessionState.STOPPED);
            this.stopBackendProcesses(session);
            this.persistRegistry();
        }
        this.sessions.remove(sessionId);
        this.playerSessions.entrySet().removeIf(entry -> sessionId.equals(entry.getValue()));
        Miniverse.LOGGER.info("Archived retained session {} for recovery and inspection.", sessionId);
    }

    public void transferAssignedPlayers(MinecraftServer server, GameSession session) {
        for (SessionGroup group : session.snapshotGroups()) {
            if (group.getState() != SessionState.RUNNING || group.getPort() == null) {
                continue;
            }

            for (UUID playerUuid : group.getPlayerUuids()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                if (player != null) {
                    this.transferPlayer(player, group);
                }
            }
        }
    }

    public void transferPlayer(ServerPlayerEntity player, SessionGroup group) {
        if (group.getState() != SessionState.RUNNING || group.getPort() == null) {
            return;
        }

        player.sendMessage(net.minecraft.text.Text.literal("Transferring you to your session..."), false);
        if (VelocityProxyBridge.isEnabled()) {
            TransitionTransferCoordinator.transferToVelocityBackend(
                player,
                VelocityProxyBridge.serverName(group),
                group.getPort(),
                "Joining " + group.getGameType().getDisplayName(),
                () -> {
                }
            );
            return;
        }

        TransitionTransferCoordinator.transfer(player, SessionServerConfig.getInstance().advertisedHost(), publicPort(group.getPort()), "Joining " + group.getGameType().getDisplayName());
    }

    public void transferPlayer(ServerPlayerEntity player, String host, int port) {
        this.transferPlayer(player, host, port, () -> {
        });
    }

    public void transferPlayer(ServerPlayerEntity player, String host, int port, Runnable afterTransferPacketSent) {
        if (host == null || host.isBlank() || port <= 0) {
            return;
        }

        player.sendMessage(net.minecraft.text.Text.literal("Returning you to the main server..."), false);
        TransitionTransferCoordinator.transfer(player, host, port, "Returning to Lobby", afterTransferPacketSent);
    }

    public synchronized List<SessionGroup> getGroups(String sessionId) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            return List.of();
        }
        return session.getGroups();
    }

    public synchronized List<SessionGroup> getAssignments(String sessionId) {
        return this.getGroups(sessionId);
    }

    public synchronized void setMaxConcurrentLaunches(int maxConcurrentLaunches) {
        int normalized = Math.clamp(maxConcurrentLaunches, 1, 64);
        SessionLauncherConfig.getInstance().setMaxConcurrentLaunches(normalized);

        int currentCore = this.launcherExecutor.getCorePoolSize();
        if (normalized > currentCore) {
            this.launcherExecutor.setMaximumPoolSize(normalized);
            this.launcherExecutor.setCorePoolSize(normalized);
        } else {
            this.launcherExecutor.setCorePoolSize(normalized);
            this.launcherExecutor.setMaximumPoolSize(normalized);
        }
        Miniverse.LOGGER.info("Updated max concurrent session launches to {}", normalized);
    }

    private CompletableFuture<SessionGroup> launchGroupAsync(GameSession session, SessionGroup group) {
        return this.launchGroupAsync(session, group, SessionOperatorSnapshot.empty());
    }

    private CompletableFuture<SessionGroup> launchGroupAsync(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot) {
        return this.launchGroupAsync(session, group, operatorSnapshot, null);
    }

    private CompletableFuture<SessionGroup> launchGroupAsync(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, @Nullable MinecraftServer server) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (server == null) {
                        return this.serverLauncher.launch(session, group, operatorSnapshot).group();
                    }
                    return this.serverLauncher.launch(session, group, operatorSnapshot, "", true, progress ->
                        SessionNetwork.broadcastLaunchProgress(server, session, progress.stage(), progress.detail(), progress.progress(), false)
                    ).group();
                } catch (IOException e) {
                    group.markFailed(e.getMessage());
                    throw new RuntimeException(e);
                }
            }, this.launcherExecutor);
        } catch (RejectedExecutionException e) {
            String message = this.launchCapacityMessage(session, group);
            group.markFailed(message);
            this.persistRegistry();
            Miniverse.LOGGER.warn(message);
            return CompletableFuture.failedFuture(new IllegalStateException(message, e));
        }
    }

    private CompletableFuture<SessionGroup> launchReplacementGroupAsync(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, String directorySuffix) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return this.serverLauncher.launch(session, group, operatorSnapshot, directorySuffix, false).group();
                } catch (IOException e) {
                    group.markFailed(e.getMessage());
                    throw new RuntimeException(e);
                }
            }, this.launcherExecutor);
        } catch (RejectedExecutionException e) {
            String message = this.launchCapacityMessage(session, group);
            group.markFailed(message);
            this.persistRegistry();
            Miniverse.LOGGER.warn(message);
            return CompletableFuture.failedFuture(new IllegalStateException(message, e));
        }
    }

    private CompletableFuture<SessionGroup> launchRetainedGroupAsync(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, Path retainedRoot, MinecraftServer server) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return this.serverLauncher.launchFromRetained(session, group, operatorSnapshot, retainedRoot, true, progress ->
                        SessionNetwork.broadcastLaunchProgress(server, session, progress.stage(), progress.detail(), progress.progress(), false)
                    ).group();
                } catch (IOException e) {
                    group.markFailed(e.getMessage());
                    throw new RuntimeException(e);
                }
            }, this.launcherExecutor);
        } catch (RejectedExecutionException e) {
            String message = this.launchCapacityMessage(session, group);
            group.markFailed(message);
            this.persistRegistry();
            Miniverse.LOGGER.warn(message);
            return CompletableFuture.failedFuture(new IllegalStateException(message, e));
        }
    }

    private ThreadPoolExecutor createLauncherExecutor() {
        SessionLauncherConfig config = SessionLauncherConfig.getInstance();
        AtomicInteger threadCounter = new AtomicInteger(1);
        return new ThreadPoolExecutor(
            config.maxConcurrentLaunches(),
            config.maxConcurrentLaunches(),
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(config.queueCapacity()),
            runnable -> {
                Thread thread = new Thread(runnable, "Miniverse-SessionLauncher-" + threadCounter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private String launchCapacityMessage(GameSession session, SessionGroup group) {
        return "Cannot queue backend launch for " + group.getDisplayName()
            + " in session " + session.getSessionId()
            + ": launcher capacity is full"
            + " (active=" + this.launcherExecutor.getActiveCount()
            + ", queued=" + this.launcherExecutor.getQueue().size()
            + ", maxConcurrent=" + this.launcherExecutor.getMaximumPoolSize()
            + ", queueCapacity=" + (this.launcherExecutor.getQueue().size() + this.launcherExecutor.getQueue().remainingCapacity())
            + "). Increase config/miniverse/session-launcher.json or wait for pending launches to finish.";
    }

    private String nextSessionId(SessionGameDescriptor gameType) {
        AtomicInteger counter = this.sessionCounters.computeIfAbsent(gameType.getCommandName(), ignored -> new AtomicInteger(1));
        String sessionId;
        do {
            sessionId = gameType.getCommandName() + "-" + counter.getAndIncrement();
        } while (this.sessions.containsKey(sessionId) || SessionRegistry.existingSessionIds().contains(sessionId));
        return sessionId;
    }

    public synchronized GameSession createSession(MinigameDefinition definition) {
        return this.createSession(SessionGameDescriptor.fromDefinition(definition));
    }

    public synchronized GameSession createSession(MinigameDefinition definition, SeedPlan seedPlan) {
        return this.createSession(SessionGameDescriptor.fromDefinition(definition), seedPlan);
    }

    private synchronized void persistRegistry() {
        SessionRegistry.writeSnapshot(this.sessions.values());
    }
}
