package dev.frost.miniverse.session;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlayerAssignment {
    private final UUID playerUuid;
    private final String playerName;
    private final String sessionId;
    private final SessionGameType gameType;
    private final SeedPlan seedPlan;
    private final String assignmentLabel;
    private final List<UUID> playerUuids;
    private final List<String> playerNames;

    private volatile SessionState state;
    private volatile Integer port;
    private volatile Path workingDirectory;
    private volatile Process process;
    private volatile String connectionAddress;
    private volatile String lastError;
    private volatile Instant launchedAt;

    public PlayerAssignment(UUID playerUuid, String playerName, String sessionId, SessionGameType gameType, SeedPlan seedPlan) {
        this(sessionId, gameType, seedPlan, playerName, List.of(playerUuid), List.of(playerName));
    }

    public PlayerAssignment(String sessionId, SessionGameType gameType, SeedPlan seedPlan, String assignmentLabel, List<UUID> playerUuids, List<String> playerNames) {
        if (playerUuids.isEmpty() || playerNames.isEmpty() || playerUuids.size() != playerNames.size()) {
            throw new IllegalArgumentException("Assignments must contain at least one player and matching UUID/name counts.");
        }

        this.playerUuid = playerUuids.get(0);
        this.playerName = playerNames.get(0);
        this.sessionId = sessionId;
        this.gameType = gameType;
        this.seedPlan = seedPlan;
        this.assignmentLabel = assignmentLabel == null || assignmentLabel.isBlank() ? this.playerName : assignmentLabel;
        this.playerUuids = List.copyOf(playerUuids);
        this.playerNames = List.copyOf(playerNames);
        this.state = SessionState.CREATED;
    }

    public UUID getPlayerUuid() {
        return this.playerUuid;
    }

    public String getPlayerName() {
        return this.playerName;
    }

    public List<UUID> getPlayerUuids() {
        return this.playerUuids;
    }

    public List<String> getPlayerNames() {
        return this.playerNames;
    }

    public String getAssignmentLabel() {
        return this.assignmentLabel;
    }

    public String getDisplayName() {
        return this.assignmentLabel + " [" + String.join(", ", this.playerNames) + "]";
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public SessionGameType getGameType() {
        return this.gameType;
    }

    public SeedPlan getSeedPlan() {
        return this.seedPlan;
    }

    public SessionState getState() {
        return this.state;
    }

    public Integer getPort() {
        return this.port;
    }

    public Path getWorkingDirectory() {
        return this.workingDirectory;
    }

    public Process getProcess() {
        return this.process;
    }

    public String getConnectionAddress() {
        return this.connectionAddress;
    }

    public String getLastError() {
        return this.lastError;
    }

    public Instant getLaunchedAt() {
        return this.launchedAt;
    }

    public int getPlayerCount() {
        return this.playerUuids.size();
    }

    public boolean containsPlayer(UUID playerUuid) {
        return this.playerUuids.contains(playerUuid);
    }

    public synchronized void markLaunching(Path workingDirectory, int port) {
        this.workingDirectory = workingDirectory;
        this.port = port;
        this.state = SessionState.LAUNCHING;
        this.lastError = null;
    }

    public synchronized void markRunning(Process process, String connectionAddress) {
        this.process = process;
        this.connectionAddress = connectionAddress;
        this.launchedAt = Instant.now();
        this.state = SessionState.RUNNING;
        this.lastError = null;
    }

    public synchronized void markStopped() {
        this.state = SessionState.STOPPED;
    }

    public synchronized void markFailed(String lastError) {
        this.state = SessionState.FAILED;
        this.lastError = lastError;
    }

    public boolean isAlive() {
        return this.process != null && this.process.isAlive();
    }
}

