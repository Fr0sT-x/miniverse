package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import net.minecraft.network.packet.s2c.common.ServerTransferS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();

    private final Map<String, GameSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, String> playerAssignments = new ConcurrentHashMap<>();
    private final Map<SessionGameType, AtomicInteger> sessionCounters = new ConcurrentHashMap<>();
    private final ExecutorService launcherExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "Miniverse-SessionLauncher");
        thread.setDaemon(true);
        return thread;
    });
    private final ServerLauncher serverLauncher = new ServerLauncher();

    private SessionManager() {
        this.registerShutdownHook();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Miniverse.LOGGER.info("Cleaning up backend session servers...");
            synchronized (this) {
                for (GameSession session : this.sessions.values()) {
                    for (PlayerAssignment assignment : session.snapshotAssignments()) {
                        if (assignment.isAlive()) {
                            Miniverse.LOGGER.info("Terminating backend process for {} in session {}", assignment.getPlayerName(), session.getSessionId());
                            this.serverLauncher.stop(assignment);
                        }
                    }
                }
            }
            Miniverse.LOGGER.info("Backend session server cleanup complete.");
        }, "Miniverse-ShutdownHook"));
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public synchronized GameSession createSession(SessionGameType gameType) {
        return this.createSession(gameType, SeedPlan.randomSameSeed());
    }

    public synchronized GameSession createSession(SessionGameType gameType, SeedPlan seedPlan) {
        String sessionId = this.nextSessionId(gameType);
        GameSession session = new GameSession(sessionId, gameType, seedPlan);
        this.sessions.put(sessionId, session);
        this.persistRegistry();
        Miniverse.LOGGER.info("Created {} session {} with seed {}", gameType.getDisplayName(), sessionId, session.getSeedPlan().sharedSeed());
        return session;
    }

    public synchronized GameSession createSession(SessionGameType gameType, Collection<ServerPlayerEntity> players) {
        GameSession session = this.createSession(gameType);
        for (ServerPlayerEntity player : players) {
            this.assignPlayer(session.getSessionId(), player);
        }
        return session;
    }

    public synchronized PlayerAssignment assignPlayers(String sessionId, String label, Collection<ServerPlayerEntity> players) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown session: " + sessionId);
        }

        List<UUID> playerUuids = new ArrayList<>();
        List<String> playerNames = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            this.playerAssignments.put(player.getUuid(), sessionId);
            playerUuids.add(player.getUuid());
            playerNames.add(player.getName().getString());
        }

        PlayerAssignment assignment = session.addAssignment(label, playerUuids, playerNames);
        this.persistRegistry();
        return assignment;
    }

    public synchronized List<GameSession> getSessions() {
        return Collections.unmodifiableList(new ArrayList<>(this.sessions.values()));
    }

    public synchronized Optional<GameSession> getSession(String sessionId) {
        return Optional.ofNullable(this.sessions.get(sessionId));
    }

    public Optional<GameSession> getSessionForPlayer(UUID playerUuid) {
        String sessionId = this.playerAssignments.get(playerUuid);
        if (sessionId == null) {
            return Optional.empty();
        }
        return this.getSession(sessionId);
    }

    public synchronized PlayerAssignment assignPlayer(String sessionId, ServerPlayerEntity player) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown session: " + sessionId);
        }

        String previousSessionId = this.playerAssignments.put(player.getUuid(), sessionId);
        if (previousSessionId != null && !previousSessionId.equals(sessionId)) {
            GameSession previousSession = this.sessions.get(previousSessionId);
            if (previousSession != null) {
                previousSession.removePlayer(player.getUuid());
            }
        }

        PlayerAssignment assignment = session.addPlayer(player.getUuid(), player.getName().getString());
        Miniverse.LOGGER.info("Assigned player {} to {} session {}", player.getName().getString(), session.getGameType().getDisplayName(), sessionId);

        if (session.getState() == SessionState.RUNNING || session.getState() == SessionState.LAUNCHING) {
            this.launchAssignmentAsync(session, assignment);
        }

        this.persistRegistry();

        return assignment;
    }

    public synchronized @Nullable PlayerAssignment unassignPlayer(UUID playerUuid) {
        String sessionId = this.playerAssignments.remove(playerUuid);
        if (sessionId == null) {
            return null;
        }

        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            return null;
        }

        PlayerAssignment removed = session.removePlayer(playerUuid);
        this.persistRegistry();
        return removed;
    }

    public synchronized void clearPlayerAssignmentsForSession(String sessionId) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            return;
        }

        for (PlayerAssignment assignment : session.snapshotAssignments()) {
            for (UUID playerUuid : assignment.getPlayerUuids()) {
                this.playerAssignments.remove(playerUuid, sessionId);
            }
        }
    }

    public CompletableFuture<GameSession> launchSession(String sessionId) {
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

        if (session.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Session has no assigned players: " + sessionId));
        }

        session.setState(SessionState.LAUNCHING);

        List<PlayerAssignment> assignments = new ArrayList<>(session.snapshotAssignments());

        if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD && assignments.size() > 0) {
            PlayerAssignment primary = assignments.get(0);
            CompletableFuture<PlayerAssignment> primaryLaunch = this.launchAssignmentAsync(session, primary);

            return primaryLaunch.thenApply(launchedAssignment -> {
                // Reuse the launched process/port for other assignments
                java.nio.file.Path workingDirectory = launchedAssignment.getWorkingDirectory();
                Integer port = launchedAssignment.getPort();
                Process process = launchedAssignment.getProcess();
                String connectionAddress = launchedAssignment.getConnectionAddress();

                for (int i = 1; i < assignments.size(); i++) {
                    PlayerAssignment other = assignments.get(i);
                    // Mark as launching and running with the same backend process
                    if (workingDirectory != null && port != null) {
                        other.markLaunching(workingDirectory, port);
                    }
                    other.markRunning(process, connectionAddress);
                }

                session.setState(SessionState.RUNNING);
                this.persistRegistry();
                return session;
            }).exceptionally(error -> {
                session.setState(SessionState.FAILED);
                this.persistRegistry();
                throw new RuntimeException(error);
            });
        }

        List<CompletableFuture<PlayerAssignment>> launches = new ArrayList<>();
        for (PlayerAssignment assignment : assignments) {
            launches.add(this.launchAssignmentAsync(session, assignment));
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
                throw new RuntimeException(error);
            });
    }

    public synchronized void stopSession(String sessionId) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            return;
        }

        session.setState(SessionState.STOPPING);
        for (PlayerAssignment assignment : session.snapshotAssignments()) {
            this.serverLauncher.stop(assignment);
        }
        session.setState(SessionState.STOPPED);
        this.persistRegistry();
    }

    public synchronized void removeSession(String sessionId) {
        this.stopSession(sessionId);
        this.sessions.remove(sessionId);
        this.playerAssignments.entrySet().removeIf(entry -> sessionId.equals(entry.getValue()));
        SessionRegistry.removeSession(sessionId);
        this.persistRegistry();
    }

    public void transferAssignedPlayers(MinecraftServer server, GameSession session) {
        for (PlayerAssignment assignment : session.snapshotAssignments()) {
            if (assignment.getState() != SessionState.RUNNING || assignment.getPort() == null) {
                continue;
            }

            for (UUID playerUuid : assignment.getPlayerUuids()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                if (player != null) {
                    this.transferPlayer(player, assignment);
                }
            }
        }
    }

    public void transferPlayer(ServerPlayerEntity player, PlayerAssignment assignment) {
        if (assignment.getState() != SessionState.RUNNING || assignment.getPort() == null) {
            return;
        }

        player.sendMessage(net.minecraft.text.Text.literal("Transferring you to your session..."), false);
        player.networkHandler.sendPacket(new ServerTransferS2CPacket("127.0.0.1", assignment.getPort()));
    }

    public void transferPlayer(ServerPlayerEntity player, String host, int port) {
        if (host == null || host.isBlank() || port <= 0) {
            return;
        }

        player.sendMessage(net.minecraft.text.Text.literal("Returning you to the main server..."), false);
        player.networkHandler.sendPacket(new ServerTransferS2CPacket(host, port));
    }

    public synchronized List<PlayerAssignment> getAssignments(String sessionId) {
        GameSession session = this.sessions.get(sessionId);
        if (session == null) {
            return List.of();
        }
        return session.getAssignments();
    }

    private CompletableFuture<PlayerAssignment> launchAssignmentAsync(GameSession session, PlayerAssignment assignment) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return this.serverLauncher.launch(session, assignment).assignment();
            } catch (IOException e) {
                assignment.markFailed(e.getMessage());
                throw new RuntimeException(e);
            }
        }, this.launcherExecutor);
    }

    private String nextSessionId(SessionGameType gameType) {
        AtomicInteger counter = this.sessionCounters.computeIfAbsent(gameType, ignored -> new AtomicInteger(1));
        return gameType.getCommandName() + "-" + counter.getAndIncrement();
    }

    private synchronized void persistRegistry() {
        SessionRegistry.writeSnapshot(this.sessions.values());
    }
}

