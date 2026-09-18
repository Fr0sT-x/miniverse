package dev.frost.miniverse.minigame.core.freeze;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DownedPlayerTracker {
    private static final Set<UUID> DOWNED_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private DownedPlayerTracker() {}

    public static void setDowned(UUID playerUuid, boolean downed) {
        if (playerUuid == null) return;
        if (downed) {
            DOWNED_PLAYERS.add(playerUuid);
        } else {
            DOWNED_PLAYERS.remove(playerUuid);
        }
    }

    public static boolean isDowned(UUID playerUuid) {
        return playerUuid != null && DOWNED_PLAYERS.contains(playerUuid);
    }

    public static void clear() {
        DOWNED_PLAYERS.clear();
    }
}
