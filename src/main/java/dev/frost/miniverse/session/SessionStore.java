package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionStore {
    private final Map<String, GameSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, String> playerSessions = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sessionCounters = new ConcurrentHashMap<>();

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

    public synchronized GameSession createSession(MinigameDefinition definition) {
        return this.createSession(SessionGameDescriptor.fromDefinition(definition));
    }

    public synchronized GameSession createSession(MinigameDefinition definition, SeedPlan seedPlan) {
        return this.createSession(SessionGameDescriptor.fromDefinition(definition), seedPlan);
    }

    public synchronized GameSession createSession(SessionGameDescriptor gameType, Collection<ServerPlayerEntity> players) {
        GameSession session = this.createSession(gameType);
        for (ServerPlayerEntity player : players) {
            this.recordPlayerSession(player.getUuid(), session.getSessionId());
        }
        return session;
    }

    public synchronized List<GameSession> getSessions() {
        return Collections.unmodifiableList(new ArrayList<>(this.sessions.values()));
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
            this.recordPlayerSession(member.playerUuid(), sessionId);
        }

        SessionGroup group = session.addGroup(plannedTeam);
        this.persistRegistry();
        return group;
    }

    public synchronized Optional<GameSession> getSession(String sessionId) {
        return Optional.ofNullable(this.sessions.get(sessionId));
    }

    public String getSessionIdForPlayer(UUID playerUuid) {
        return this.playerSessions.get(playerUuid);
    }

    public Optional<GameSession> getSessionForPlayer(UUID playerUuid) {
        String sessionId = this.playerSessions.get(playerUuid);
        if (sessionId == null) {
            return Optional.empty();
        }
        return this.getSession(sessionId);
    }

    public synchronized void removeSession(String sessionId) {
        this.sessions.remove(sessionId);
        this.playerSessions.entrySet().removeIf(entry -> sessionId.equals(entry.getValue()));
        SessionRegistry.removeSession(sessionId);
        this.persistRegistry();
    }

    public synchronized void putSession(GameSession session) {
        this.sessions.put(session.getSessionId(), session);
        this.persistRegistry();
    }

    public synchronized void archiveSession(String sessionId) {
        this.sessions.remove(sessionId);
        this.playerSessions.entrySet().removeIf(entry -> sessionId.equals(entry.getValue()));
        Miniverse.LOGGER.info("Archived retained session {} for recovery and inspection.", sessionId);
    }

    public String recordPlayerSession(UUID playerUuid, String sessionId) {
        return this.playerSessions.put(playerUuid, sessionId);
    }

    public void removePlayerSession(UUID playerUuid, String sessionId) {
        this.playerSessions.remove(playerUuid, sessionId);
    }

    public String removePlayerSession(UUID playerUuid) {
        return this.playerSessions.remove(playerUuid);
    }

    private String nextSessionId(SessionGameDescriptor gameType) {
        AtomicInteger counter = this.sessionCounters.computeIfAbsent(gameType.getCommandName(), ignored -> new AtomicInteger(1));
        String sessionId;
        do {
            sessionId = gameType.getCommandName() + "-" + counter.getAndIncrement();
        } while (this.sessions.containsKey(sessionId) || SessionRegistry.existingSessionIds().contains(sessionId));
        return sessionId;
    }

    public synchronized void persistRegistry() {
        SessionRegistry.writeSnapshot(this.sessions.values());
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
}
