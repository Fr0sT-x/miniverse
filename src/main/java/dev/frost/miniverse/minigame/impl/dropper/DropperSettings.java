package dev.frost.miniverse.minigame.impl.dropper;

import net.minecraft.nbt.NbtCompound;

import java.util.Properties;

public record DropperSettings(
    String mapId,
    int levelsToPlay,
    String selectionMode,
    String selectedLevelIds,
    int finalCountdownSeconds,
    int timeLimitSeconds,
    boolean allowSkip,
    int skipFailsThreshold
) {
    public static DropperSettings defaults() {
        return new DropperSettings("", 0, "ORDER", "", 60, 600, true, 20);
    }

    public static DropperSettings fromNbt(NbtCompound nbt) {
        if (nbt == null) {
            return defaults();
        }
        return new DropperSettings(
            nbt.contains("mapId") ? nbt.getString("mapId") : "",
            nbt.contains("levelsToPlay") ? nbt.getInt("levelsToPlay") : 0,
            nbt.contains("selectionMode") ? nbt.getString("selectionMode") : "ORDER",
            nbt.contains("selectedLevelIds") ? nbt.getString("selectedLevelIds") : "",
            nbt.contains("finalCountdownSeconds") ? nbt.getInt("finalCountdownSeconds") : 60,
            nbt.contains("timeLimitSeconds") ? nbt.getInt("timeLimitSeconds") : 600,
            !nbt.contains("allowSkip") || nbt.getBoolean("allowSkip"),
            nbt.contains("skipFailsThreshold") ? nbt.getInt("skipFailsThreshold") : 20
        );
    }

    public void writeTo(Properties properties) {
        properties.setProperty("dropper.mapId", this.mapId != null ? this.mapId : "");
        properties.setProperty("dropper.levelsToPlay", String.valueOf(this.levelsToPlay));
        properties.setProperty("dropper.selectionMode", this.selectionMode != null ? this.selectionMode : "ORDER");
        properties.setProperty("dropper.selectedLevelIds", this.selectedLevelIds != null ? this.selectedLevelIds : "");
        properties.setProperty("dropper.finalCountdownSeconds", String.valueOf(this.finalCountdownSeconds));
        properties.setProperty("dropper.timeLimitSeconds", String.valueOf(this.timeLimitSeconds));
        properties.setProperty("dropper.allowSkip", String.valueOf(this.allowSkip));
        properties.setProperty("dropper.skipFailsThreshold", String.valueOf(this.skipFailsThreshold));
    }

    public static DropperSettings fromProperties(Properties properties) {
        if (properties == null) {
            return defaults();
        }
        return new DropperSettings(
            properties.getProperty("dropper.mapId", ""),
            parseInt(properties.getProperty("dropper.levelsToPlay"), 0),
            properties.getProperty("dropper.selectionMode", "ORDER"),
            properties.getProperty("dropper.selectedLevelIds", ""),
            parseInt(properties.getProperty("dropper.finalCountdownSeconds"), 60),
            parseInt(properties.getProperty("dropper.timeLimitSeconds"), 600),
            Boolean.parseBoolean(properties.getProperty("dropper.allowSkip", "true")),
            parseInt(properties.getProperty("dropper.skipFailsThreshold"), 20)
        );
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
