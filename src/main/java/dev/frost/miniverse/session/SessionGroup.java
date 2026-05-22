package dev.frost.miniverse.session;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Backend launch group inside a session.
 *
 * A group owns one planned team plus the backend process state used to host
 * that team. Shared-world sessions can attach multiple groups to the same backend.
 */
public final class SessionGroup {
    private final UUID primaryPlayerUuid;
    private final String primaryPlayerName;
    private final String sessionId;
    private final SessionGameDescriptor gameType;
    private SeedPlan seedPlan;
    private final PlannedTeam plannedTeam;
    private final BackendInstance backendInstance = new BackendInstance();

    public SessionGroup(UUID playerUuid, String playerName, String sessionId, SessionGameDescriptor gameType, SeedPlan seedPlan) {
        this(sessionId, gameType, seedPlan, "Team 1", List.of(playerUuid), List.of(playerName));
    }

    public SessionGroup(String sessionId, SessionGameDescriptor gameType, SeedPlan seedPlan, String groupLabel, List<UUID> playerUuids, List<String> playerNames) {
        this(sessionId, gameType, seedPlan, new PlannedTeam(groupLabel, membershipsFrom(playerUuids, playerNames)));
    }

    public SessionGroup(String sessionId, SessionGameDescriptor gameType, SeedPlan seedPlan, PlannedTeam plannedTeam) {
        if (plannedTeam == null || plannedTeam.members().isEmpty()) {
            throw new IllegalArgumentException("Session groups require a non-empty planned team.");
        }

        this.primaryPlayerUuid = plannedTeam.members().getFirst().playerUuid();
        this.primaryPlayerName = plannedTeam.members().getFirst().playerName();
        this.sessionId = sessionId;
        this.gameType = gameType;
        this.seedPlan = seedPlan;
        this.plannedTeam = plannedTeam;
    }

    public UUID getPrimaryPlayerUuid() {
        return this.primaryPlayerUuid;
    }

    public String getPrimaryPlayerName() {
        return this.primaryPlayerName;
    }

    public List<UUID> getPlayerUuids() {
        return this.plannedTeam.playerUuids();
    }

    public List<String> getPlayerNames() {
        return this.plannedTeam.playerNames();
    }

    public String getGroupLabel() {
        return this.plannedTeam.label();
    }

    public PlannedTeam getPlannedTeam() {
        return this.plannedTeam;
    }

    public String getDisplayName() {
        return this.plannedTeam.displayName();
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public SessionGameDescriptor getGameType() {
        return this.gameType;
    }

    public SeedPlan getSeedPlan() {
        return this.seedPlan;
    }

    public synchronized void setSeedPlan(SeedPlan seedPlan) {
        if (seedPlan == null) {
            throw new IllegalArgumentException("Seed plan cannot be null.");
        }
        this.seedPlan = seedPlan;
    }

    public BackendInstance getBackendInstance() {
        return this.backendInstance;
    }

    public SessionState getState() {
        return this.backendInstance.getState();
    }

    public Integer getPort() {
        return this.backendInstance.getPort();
    }

    public Path getWorkingDirectory() {
        return this.backendInstance.getWorkingDirectory();
    }

    public Process getProcess() {
        return this.backendInstance.getProcess();
    }

    public String getConnectionAddress() {
        return this.backendInstance.getConnectionAddress();
    }

    public String getLastError() {
        return this.backendInstance.getLastError();
    }

    public Instant getLaunchedAt() {
        return this.backendInstance.getLaunchedAt();
    }

    public int getPlayerCount() {
        return this.plannedTeam.playerCount();
    }

    public boolean containsPlayer(UUID playerUuid) {
        return this.plannedTeam.containsPlayer(playerUuid);
    }

    public void attachBackend(BackendInstance backendInstance) {
        if (backendInstance.getWorkingDirectory() != null && backendInstance.getPort() != null) {
            this.backendInstance.markLaunching(backendInstance.getWorkingDirectory(), backendInstance.getPort());
        }
        this.backendInstance.markRunning(backendInstance.getProcess(), backendInstance.getConnectionAddress());
    }

    public synchronized void markLaunching(Path workingDirectory, int port) {
        this.backendInstance.markLaunching(workingDirectory, port);
    }

    public synchronized void markRunning(Process process, String connectionAddress) {
        this.backendInstance.markRunning(process, connectionAddress);
    }

    public synchronized void markStopped() {
        this.backendInstance.markStopped();
    }

    public synchronized void markFailed(String lastError) {
        this.backendInstance.markFailed(lastError);
    }

    public boolean isAlive() {
        return this.backendInstance.isAlive();
    }

    private static List<SessionMembership> membershipsFrom(List<UUID> playerUuids, List<String> playerNames) {
        if (playerUuids.isEmpty() || playerNames.isEmpty() || playerUuids.size() != playerNames.size()) {
            throw new IllegalArgumentException("Session groups must contain at least one player and matching UUID/name counts.");
        }
        return java.util.stream.IntStream.range(0, playerUuids.size())
            .mapToObj(index -> new SessionMembership(playerUuids.get(index), playerNames.get(index), ""))
            .toList();
    }
}
