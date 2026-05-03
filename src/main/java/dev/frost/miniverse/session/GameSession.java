package dev.frost.miniverse.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;

public final class GameSession {
    private final String sessionId;
    private final SessionGameType gameType;
    private final SeedPlan seedPlan;
    private final Instant createdAt;
    private final List<PlayerAssignment> assignments = new ArrayList<>();
    private NbtCompound settings = new NbtCompound();

    private volatile SessionState state;
    private volatile Instant launchedAt;

    public GameSession(String sessionId, SessionGameType gameType, SeedPlan seedPlan) {
        this.sessionId = sessionId;
        this.gameType = gameType;
        this.seedPlan = seedPlan;
        this.createdAt = Instant.now();
        this.state = SessionState.CREATED;
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

    public synchronized NbtCompound getSettings() {
        return this.settings.copy();
    }

    public synchronized void setSettings(NbtCompound settings) {
        this.settings = settings == null ? new NbtCompound() : settings.copy();
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public SessionState getState() {
        return this.state;
    }

    public Instant getLaunchedAt() {
        return this.launchedAt;
    }

    public synchronized PlayerAssignment addPlayer(UUID playerUuid, String playerName) {
        return this.addAssignment(playerName, List.of(playerUuid), List.of(playerName));
    }

    public synchronized PlayerAssignment addAssignment(String label, Collection<UUID> playerUuids, Collection<String> playerNames) {
        if (playerUuids.isEmpty() || playerNames.isEmpty() || playerUuids.size() != playerNames.size()) {
            throw new IllegalArgumentException("Assignments must contain at least one player and matching UUID/name counts.");
        }

        PlayerAssignment assignment = new PlayerAssignment(
            this.sessionId,
            this.gameType,
            this.seedPlan,
            label,
            new ArrayList<>(playerUuids),
            new ArrayList<>(playerNames)
        );
        this.assignments.add(assignment);
        return assignment;
    }

    public synchronized Optional<PlayerAssignment> getAssignment(UUID playerUuid) {
        return this.assignments.stream().filter(assignment -> assignment.containsPlayer(playerUuid)).findFirst();
    }

    public synchronized List<PlayerAssignment> getAssignments() {
        return Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(this.assignments)));
    }

    public synchronized boolean containsPlayer(UUID playerUuid) {
        return this.assignments.stream().anyMatch(assignment -> assignment.containsPlayer(playerUuid));
    }

    public synchronized PlayerAssignment removePlayer(UUID playerUuid) {
        PlayerAssignment assignment = this.getAssignment(playerUuid).orElse(null);
        if (assignment != null) {
            this.assignments.remove(assignment);
        }
        return assignment;
    }

    public synchronized boolean isEmpty() {
        return this.assignments.isEmpty();
    }

    public synchronized void setState(SessionState state) {
        this.state = state;
        if (state == SessionState.RUNNING && this.launchedAt == null) {
            this.launchedAt = Instant.now();
        }
    }

    public synchronized Collection<PlayerAssignment> snapshotAssignments() {
        return List.copyOf(new LinkedHashSet<>(this.assignments));
    }
}


