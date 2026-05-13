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
    private final SessionGameDescriptor gameType;
    private final SeedPlan seedPlan;
    private final Instant createdAt;
    private final List<SessionGroup> groups = new ArrayList<>();
    private NbtCompound settings = new NbtCompound();

    private volatile SessionState state;
    private volatile Instant launchedAt;

    public GameSession(String sessionId, SessionGameDescriptor gameType, SeedPlan seedPlan) {
        this.sessionId = sessionId;
        this.gameType = gameType;
        this.seedPlan = seedPlan;
        this.createdAt = Instant.now();
        this.state = SessionState.CREATED;
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

    public synchronized SessionGroup addPlayer(UUID playerUuid, String playerName) {
        return this.addGroup(playerName, List.of(playerUuid), List.of(playerName));
    }

    public synchronized SessionGroup addGroup(PlannedTeam plannedTeam) {
        SessionGroup group = new SessionGroup(
            this.sessionId,
            this.gameType,
            this.seedPlan,
            plannedTeam
        );
        this.groups.add(group);
        return group;
    }

    public synchronized SessionGroup addGroup(String label, Collection<UUID> playerUuids, Collection<String> playerNames) {
        if (playerUuids.isEmpty() || playerNames.isEmpty() || playerUuids.size() != playerNames.size()) {
            throw new IllegalArgumentException("Session groups must contain at least one player and matching UUID/name counts.");
        }

        SessionGroup group = new SessionGroup(
            this.sessionId,
            this.gameType,
            this.seedPlan,
            label,
            new ArrayList<>(playerUuids),
            new ArrayList<>(playerNames)
        );
        this.groups.add(group);
        return group;
    }

    public synchronized Optional<SessionGroup> getGroup(UUID playerUuid) {
        return this.groups.stream().filter(group -> group.containsPlayer(playerUuid)).findFirst();
    }

    public synchronized List<SessionGroup> getGroups() {
        return Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(this.groups)));
    }

    public synchronized boolean containsPlayer(UUID playerUuid) {
        return this.groups.stream().anyMatch(group -> group.containsPlayer(playerUuid));
    }

    public synchronized SessionGroup removePlayer(UUID playerUuid) {
        SessionGroup group = this.getGroup(playerUuid).orElse(null);
        if (group != null) {
            this.groups.remove(group);
        }
        return group;
    }

    public synchronized boolean isEmpty() {
        return this.groups.isEmpty();
    }

    public synchronized void setState(SessionState state) {
        this.state = state;
        if (state == SessionState.RUNNING && this.launchedAt == null) {
            this.launchedAt = Instant.now();
        }
    }

    public synchronized Collection<SessionGroup> snapshotGroups() {
        return List.copyOf(new LinkedHashSet<>(this.groups));
    }

    public synchronized SessionGroup addAssignment(String label, Collection<UUID> playerUuids, Collection<String> playerNames) {
        return this.addGroup(label, playerUuids, playerNames);
    }

    public synchronized Optional<SessionGroup> getAssignment(UUID playerUuid) {
        return this.getGroup(playerUuid);
    }

    public synchronized List<SessionGroup> getAssignments() {
        return this.getGroups();
    }

    public synchronized Collection<SessionGroup> snapshotAssignments() {
        return this.snapshotGroups();
    }
}


