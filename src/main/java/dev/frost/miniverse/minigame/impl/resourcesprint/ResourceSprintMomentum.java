package dev.frost.miniverse.minigame.impl.resourcesprint;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight momentum / streak tracker.
 */
public final class ResourceSprintMomentum {
    private UUID lastClaimer = null;
    private final Map<UUID, Integer> streaks = new HashMap<>();

    public int onClaim(UUID claimer) {
        if (claimer == null) return 0;
        if (claimer.equals(lastClaimer)) {
            int next = streaks.getOrDefault(claimer, 1) + 1;
            streaks.put(claimer, next);
            lastClaimer = claimer;
            return next;
        } else {
            // reset previous claimer streak
            if (lastClaimer != null) {
                streaks.put(lastClaimer, 0);
            }
            streaks.put(claimer, 1);
            lastClaimer = claimer;
            return 1;
        }
    }

    public int getStreak(UUID player) {
        return streaks.getOrDefault(player, 0);
    }

    public void reset() {
        streaks.clear();
        lastClaimer = null;
    }

    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        if (lastClaimer != null) {
            json.addProperty("lastClaimer", lastClaimer.toString());
        }
        com.google.gson.JsonObject streaksObj = new com.google.gson.JsonObject();
        streaks.forEach((uuid, streak) -> streaksObj.addProperty(uuid.toString(), streak));
        json.add("streaks", streaksObj);
        return json;
    }

    public void loadRuntimeState(com.google.gson.JsonObject json) {
        reset();
        if (json.has("lastClaimer")) {
            lastClaimer = UUID.fromString(json.get("lastClaimer").getAsString());
        }
        if (json.has("streaks")) {
            com.google.gson.JsonObject streaksObj = json.getAsJsonObject("streaks");
            for (String key : streaksObj.keySet()) {
                streaks.put(UUID.fromString(key), streaksObj.get(key).getAsInt());
            }
        }
    }
}

