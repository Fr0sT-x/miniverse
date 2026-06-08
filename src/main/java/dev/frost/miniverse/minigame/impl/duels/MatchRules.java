package dev.frost.miniverse.minigame.impl.duels;

import java.util.List;

public record MatchRules(
    boolean allowBlockPlacement,
    boolean allowBlockBreaking,
    boolean allowLiquidFlow,
    boolean disableDamage,
    boolean trackHits,
    List<String> requiredArenaTags,
    int winConditionScore
) {
    public static MatchRules defaultRules() {
        return new MatchRules(false, false, false, false, false, List.of(), 1);
    }
}
