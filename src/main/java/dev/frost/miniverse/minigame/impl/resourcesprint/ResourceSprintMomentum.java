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
}

