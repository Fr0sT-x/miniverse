package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import net.minecraft.network.packet.s2c.common.ServerTransferS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final ThreadPoolExecutor launcherExecutor = this.createLauncherExecutor();
    private final ServerLauncher serverLauncher = new ServerLauncher();

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

        if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD && groups.size() > 0) {
            SessionGroup primary = groups.get(0);
            CompletableFuture<SessionGroup> primaryLaunch = this.launchGroupAsync(session, primary);

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
            launches.add(this.launchGroupAsync(session, group));
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
        for (SessionGroup group : session.snapshotGroups()) {
            this.serverLauncher.stop(group);
        }
        session.setState(SessionState.STOPPED);
        this.persistRegistry();
    }

    public synchronized void removeSession(String sessionId) {
        this.stopSession(sessionId);
        this.sessions.remove(sessionId);
        this.playerSessions.entrySet().removeIf(entry -> sessionId.equals(entry.getValue()));
        SessionRegistry.removeSession(sessionId);
        this.persistRegistry();
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
        player.networkHandler.sendPacket(new ServerTransferS2CPacket("127.0.0.1", group.getPort()));
    }

    public void transferPlayer(ServerPlayerEntity player, String host, int port) {
        if (host == null || host.isBlank() || port <= 0) {
            return;
        }

        player.sendMessage(net.minecraft.text.Text.literal("Returning you to the main server..."), false);
        player.networkHandler.sendPacket(new ServerTransferS2CPacket(host, port));
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
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return this.serverLauncher.launch(session, group).group();
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
        return gameType.getCommandName() + "-" + counter.getAndIncrement();
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