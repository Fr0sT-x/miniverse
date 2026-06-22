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

    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("allowBlockPlacement", allowBlockPlacement);
        json.addProperty("allowBlockBreaking", allowBlockBreaking);
        json.addProperty("allowLiquidFlow", allowLiquidFlow);
        json.addProperty("disableDamage", disableDamage);
        json.addProperty("trackHits", trackHits);
        
        com.google.gson.JsonArray tagsArr = new com.google.gson.JsonArray();
        for (String t : requiredArenaTags) tagsArr.add(t);
        json.add("requiredArenaTags", tagsArr);
        
        json.addProperty("winConditionScore", winConditionScore);
        json.addProperty("totalRounds", totalRounds);
        return json;
    }

    public static MatchRules loadRuntimeState(com.google.gson.JsonObject json) {
        if (json == null) return defaultRules();
        boolean allowPlacement = json.has("allowBlockPlacement") && json.get("allowBlockPlacement").getAsBoolean();
        boolean allowBreaking = json.has("allowBlockBreaking") && json.get("allowBlockBreaking").getAsBoolean();
        boolean allowLiquidFlow = json.has("allowLiquidFlow") && json.get("allowLiquidFlow").getAsBoolean();
        boolean disableDamage = json.has("disableDamage") && json.get("disableDamage").getAsBoolean();
        boolean trackHits = json.has("trackHits") && json.get("trackHits").getAsBoolean();
        
        java.util.List<String> requiredArenaTags = new java.util.ArrayList<>();
        if (json.has("requiredArenaTags")) {
            for (com.google.gson.JsonElement el : json.getAsJsonArray("requiredArenaTags")) {
                requiredArenaTags.add(el.getAsString());
            }
        }
        
        int winConditionScore = json.has("winConditionScore") ? json.get("winConditionScore").getAsInt() : 1;
        int totalRounds = json.has("totalRounds") ? json.get("totalRounds").getAsInt() : 1;
        
        return new MatchRules(allowPlacement, allowBreaking, allowLiquidFlow, disableDamage, trackHits, requiredArenaTags, winConditionScore, totalRounds);
    }
}
