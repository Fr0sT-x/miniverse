package dev.frost.miniverse.minigame.impl.duels;

import java.util.List;

public record MatchRules(
    boolean allowBlockPlacement,
    boolean allowBlockBreaking,
    boolean allowLiquidFlow,
    boolean disableDamage,
    boolean trackHits,
    List<String> requiredArenaTags,
    int winConditionScore,
    /** Total number of rounds to play. Must be a positive odd integer (1, 3, 5, …). */
    int totalRounds
) {
    public static MatchRules defaultRules() {
        return new MatchRules(false, false, false, false, false, List.of(), 1, 1);
    }

    /** Returns the number of round wins needed to win the overall match (ceil of totalRounds / 2). */
    public int roundsToWin() {
        return (totalRounds / 2) + 1;
    }
}
