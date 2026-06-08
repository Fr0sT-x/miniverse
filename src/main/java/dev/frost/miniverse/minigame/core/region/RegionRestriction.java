package dev.frost.miniverse.minigame.core.region;

import java.util.EnumSet;

public enum RegionRestriction {
    BUILD_DENIED(0xFFFF0000), // Red
    BREAK_DENIED(0xFFFFA500), // Orange
    PVP_DENIED(0xFF800080); // Purple

    private final int color;

    RegionRestriction(int color) {
        this.color = color;
    }

    public int color() {
        return this.color;
    }

    public static EnumSet<RegionRestriction> parse(com.google.gson.JsonObject properties) {
        EnumSet<RegionRestriction> restrictions = EnumSet.noneOf(RegionRestriction.class);
        if (properties != null && properties.has("restrictions") && properties.get("restrictions").isJsonArray()) {
            com.google.gson.JsonArray array = properties.getAsJsonArray("restrictions");
            for (com.google.gson.JsonElement element : array) {
                try {
                    restrictions.add(RegionRestriction.valueOf(element.getAsString().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    // Ignore unknown restrictions
                }
            }
        }
        return restrictions;
    }
}
