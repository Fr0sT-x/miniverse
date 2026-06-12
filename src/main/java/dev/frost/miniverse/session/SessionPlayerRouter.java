package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SessionPlayerRouter {
    private final SessionStore store;
    private final SessionLaunchPipeline pipeline;

    public SessionPlayerRouter(SessionStore store, SessionLaunchPipeline pipeline) {
        this.store = store;
        this.pipeline = pipeline;
    }

    public synchronized SessionGroup assignPlayer(String sessionId, ServerPlayerEntity player) {
        GameSession session = this.store.getSession(sessionId).orElse(null);
        if (session == null) {
            throw new IllegalArgumentException("Unknown session: " + sessionId);
        }

        String previousSessionId = this.store.recordPlayerSession(player.getUuid(), sessionId);
        if (previousSessionId != null && !previousSessionId.equals(sessionId)) {
            GameSession previousSession = this.store.getSession(previousSessionId).orElse(null);
            if (previousSession != null) {
                previousSession.removePlayer(player.getUuid());
            }
        }

        SessionGroup group = session.addPlayer(player.getUuid(), player.getName().getString());
        Miniverse.LOGGER.info("Assigned player {} to {} session {}", player.getName().getString(), session.getGameType().getDisplayName(), sessionId);

        if (session.getState() == SessionState.RUNNING || session.getState() == SessionState.LAUNCHING) {
            this.pipeline.launchGroupAsync(session, group);
        }

        this.store.persistRegistry();

        return group;
    }

    public synchronized SessionGroup assignPlayers(String sessionId, String label, Collection<ServerPlayerEntity> players) {
        return this.store.createGroup(sessionId, label, players);
    }

    public synchronized SessionGroup assignPlayerMidGame(String sessionId, ServerPlayerEntity player, String teamLabel, String role) {
        return this.assignPlayerMidGameInternal(sessionId, player, teamLabel, role);
    }

    public CompletableFuture<SessionGroup> assignPlayerMidGameAsync(String sessionId, ServerPlayerEntity player, String teamLabel, String role, MinecraftServer server) {
        SessionGroup group;
        boolean launchNewBackend;
        synchronized (this) {
            group = this.assignPlayerMidGameInternal(sessionId, player, teamLabel, role);
            GameSession session = this.store.getSession(sessionId).orElse(null);
            launchNewBackend = session != null
                && session.getGameType().getTopology() == SessionTopology.ISOLATED_WORLD
                && group.getState() != SessionState.RUNNING;
            if (!launchNewBackend) {
                return CompletableFuture.completedFuture(group);
            }
        }

        GameSession session;
        synchronized (this) {
            session = this.store.getSession(sessionId).orElse(null);
        }
        if (session == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown session: " + sessionId));
        }

        SessionOperatorSnapshot operatorSnapshot = SessionOperatorSnapshot.capture(server, List.of(group));
        return this.pipeline.launchGroupAsync(session, group, operatorSnapshot, server)
            .thenApply(launchedGroup -> {
                synchronized (this) {
                    this.store.persistRegistry();
                }
                return launchedGroup;
            });
    }

    private SessionGroup assignPlayerMidGameInternal(String sessionId, ServerPlayerEntity player, String teamLabel, String role) {
        GameSession session = this.store.getSession(sessionId).orElse(null);
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
        String previousSessionId = this.store.getSessionIdForPlayer(player.getUuid());
        if (previousSessionId != null && !previousSessionId.equals(sessionId)) {
            GameSession previousSession = this.store.getSession(previousSessionId).orElse(null);
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

        this.store.recordPlayerSession(player.getUuid(), sessionId);
        this.store.persistRegistry();
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
        return session.getGameType().definition().lateJoinPolicy().resolveRole(role);
    }

    private String resolveLateJoinTeam(GameSession session, String teamLabel, String role) {
        return session.getGameType().definition().lateJoinPolicy().resolveTeam(session, teamLabel, role);
    }

    public synchronized @Nullable SessionGroup unassignPlayer(UUID playerUuid) {
        String sessionId = this.store.removePlayerSession(playerUuid);
        if (sessionId == null) {
            return null;
        }

        GameSession session = this.store.getSession(sessionId).orElse(null);
        if (session == null) {
            return null;
        }

        SessionGroup removed = session.removePlayer(playerUuid);
        this.store.persistRegistry();
        return removed;
    }

    public synchronized void clearPlayerSessionsForSession(String sessionId) {
        GameSession session = this.store.getSession(sessionId).orElse(null);
        if (session == null) {
            return;
        }

        for (SessionGroup group : session.snapshotGroups()) {
            for (UUID playerUuid : group.getPlayerUuids()) {
                this.store.removePlayerSession(playerUuid, sessionId);
            }
        }
    }

    public synchronized void clearSessionGroupsForSession(String sessionId) {
        this.clearPlayerSessionsForSession(sessionId);
    }
}
