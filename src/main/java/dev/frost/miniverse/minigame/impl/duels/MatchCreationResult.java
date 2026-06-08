package dev.frost.miniverse.minigame.impl.duels;

import java.util.Optional;

public record MatchCreationResult(
    boolean success,
    Optional<DuelMatch> match,
    String userFacingError,
    String debugLogMessage
) {
    public static MatchCreationResult success(DuelMatch match) {
        return new MatchCreationResult(true, Optional.of(match), null, null);
    }

    public static MatchCreationResult failure(String userError, String debugLog) {
        return new MatchCreationResult(false, Optional.empty(), userError, debugLog);
    }
}
