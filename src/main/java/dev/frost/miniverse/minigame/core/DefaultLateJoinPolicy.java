package dev.frost.miniverse.minigame.core;

import dev.frost.miniverse.session.GameSession;
import dev.frost.miniverse.session.SessionGroup;
import dev.frost.miniverse.session.SessionState;
import dev.frost.miniverse.session.SessionTopology;

public class DefaultLateJoinPolicy implements LateJoinPolicy {
    @Override
    public String resolveRole(String requestedRole) {
        return requestedRole == null ? "" : requestedRole.trim();
    }

    @Override
    public String resolveTeam(GameSession session, String requestedTeam, String resolvedRole) {
        if (requestedTeam != null && !requestedTeam.isBlank()) {
            return requestedTeam.trim();
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

    @Override
    public boolean validateRole(String role) {
        return true;
    }
}
