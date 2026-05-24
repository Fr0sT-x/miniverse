package dev.frost.miniverse.minigame.core.protection;

import java.util.Locale;

public enum ProtectionOverlayStyle {
    VANILLA_GLOW("vanilla_glow"),
    SLIM_GLOW("slim_glow"),
    FILLED_GLOW("filled_glow");

    private final String id;

    ProtectionOverlayStyle(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static ProtectionOverlayStyle byId(String id) {
        if (id != null) {
            String normalized = id.toLowerCase(Locale.ROOT);
            for (ProtectionOverlayStyle style : values()) {
                if (style.id.equals(normalized) || style.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return style;
                }
            }
        }
        return VANILLA_GLOW;
    }
}
