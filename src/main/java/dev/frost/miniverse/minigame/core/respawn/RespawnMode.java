package dev.frost.miniverse.minigame.core.respawn;

import java.util.Locale;

public enum RespawnMode {
    POINTS,
    ELIMINATION;

    public static RespawnMode parse(String value, RespawnMode fallback) {
        try {
            return value == null || value.isBlank() ? fallback : RespawnMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public String configValue() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
