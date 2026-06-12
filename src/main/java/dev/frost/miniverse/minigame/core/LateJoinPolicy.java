package dev.frost.miniverse.minigame.core;

import dev.frost.miniverse.session.GameSession;

public interface LateJoinPolicy {
    /**
     * Normalize/validate the requested role. Throw IllegalArgumentException if invalid.
     */
    String resolveRole(String requestedRole);

    /**
     * Resolve which team the late-joiner should be placed on.
     */
    String resolveTeam(GameSession session, String requestedTeam, String resolvedRole);

    /**
     * Validate that the given role is acceptable for late-joining.
     */
    boolean validateRole(String role);
}
