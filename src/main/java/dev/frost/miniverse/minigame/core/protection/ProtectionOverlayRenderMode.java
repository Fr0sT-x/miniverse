package dev.frost.miniverse.minigame.core.protection;

import java.util.Locale;

public enum ProtectionOverlayRenderMode {
    THROUGH_WALLS("through_walls"),
    DEPTH_TESTED("depth_tested");

    private final String id;

    ProtectionOverlayRenderMode(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static ProtectionOverlayRenderMode byId(String id) {
        if (id != null) {
            String normalized = id.toLowerCase(Locale.ROOT);
            for (ProtectionOverlayRenderMode mode : values()) {
                if (mode.id.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return mode;
                }
            }
        }
        return DEPTH_TESTED;
    }
}
