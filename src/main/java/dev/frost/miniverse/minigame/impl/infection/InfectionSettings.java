package dev.frost.miniverse.minigame.impl.infection;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.Properties;

public record InfectionSettings(
    int matchDurationSeconds,
    int startingInfectedCount,
    int respawnDelaySeconds,
    boolean allowFriendlyFire,
    String mapId
) {
    public static InfectionSettings defaults() {
        return new InfectionSettings(600, 1, 3, false, "");
    }

    public static InfectionSettings fromNbt(NbtCompound nbt) {
        InfectionSettings defaults = defaults();
        return new InfectionSettings(
            intValue(nbt, "matchDurationSeconds", defaults.matchDurationSeconds),
            intValue(nbt, "startingInfectedCount", defaults.startingInfectedCount),
            intValue(nbt, "respawnDelaySeconds", defaults.respawnDelaySeconds),
            boolValue(nbt, "allowFriendlyFire", defaults.allowFriendlyFire),
            stringValue(nbt, "mapId", defaults.mapId)
        ).clamped();
    }

    public static InfectionSettings fromProperties(Properties properties) {
        InfectionSettings defaults = defaults();
        return new InfectionSettings(
            intValue(properties, "infection.matchDurationSeconds", defaults.matchDurationSeconds),
            intValue(properties, "infection.startingInfectedCount", defaults.startingInfectedCount),
            intValue(properties, "infection.respawnDelaySeconds", defaults.respawnDelaySeconds),
            Boolean.parseBoolean(properties.getProperty("infection.allowFriendlyFire", Boolean.toString(defaults.allowFriendlyFire))),
            properties.getProperty("infection.mapId", properties.getProperty("map.id", defaults.mapId))
        ).clamped();
    }

    public void writeTo(Properties properties) {
        InfectionSettings settings = this.clamped();
        properties.setProperty("infection.matchDurationSeconds", Integer.toString(settings.matchDurationSeconds));
        properties.setProperty("infection.startingInfectedCount", Integer.toString(settings.startingInfectedCount));
        properties.setProperty("infection.respawnDelaySeconds", Integer.toString(settings.respawnDelaySeconds));
        properties.setProperty("infection.allowFriendlyFire", Boolean.toString(settings.allowFriendlyFire));
        properties.setProperty("infection.mapId", settings.mapId);
    }

    private InfectionSettings clamped() {
        return new InfectionSettings(
            Math.clamp(this.matchDurationSeconds, 60, 7200),
            Math.clamp(this.startingInfectedCount, 1, 16),
            Math.clamp(this.respawnDelaySeconds, 0, 30),
            this.allowFriendlyFire,
            this.mapId == null ? "" : this.mapId.trim()
        );
    }

    private static int intValue(NbtCompound nbt, String key, int fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE) ? nbt.getInt(key) : fallback;
    }

    private static boolean boolValue(NbtCompound nbt, String key, boolean fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE) ? nbt.getBoolean(key) : fallback;
    }

    private static String stringValue(NbtCompound nbt, String key, String fallback) {
        return nbt != null && nbt.contains(key, NbtElement.STRING_TYPE) ? nbt.getString(key) : fallback;
    }

    private static int intValue(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
