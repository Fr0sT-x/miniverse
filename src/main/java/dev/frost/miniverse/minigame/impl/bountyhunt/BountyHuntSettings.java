package dev.frost.miniverse.minigame.impl.bountyhunt;

import net.minecraft.nbt.NbtCompound;

import java.util.Properties;

public record BountyHuntSettings(
    int gracePeriodSeconds,
    int respawnInvincibilitySeconds,
    int scoreToWin,
    int targetSwapIntervalSeconds,
    boolean trackerEnabled,
    boolean netherTrackingEnabled,
    int compassCooldownSeconds,
    String trackerItemId,
    int disconnectGraceSeconds
) {
    public static BountyHuntSettings defaults() {
        int globalGrace = Integer.getInteger("miniverse.lifecycle.disconnectGraceSeconds", 300);
        int modeGrace = Integer.getInteger("miniverse.bountyhunt.disconnectGraceSeconds", globalGrace);
        return new BountyHuntSettings(
            Integer.getInteger("miniverse.bountyhunt.gracePeriodSeconds", 300),
            Integer.getInteger("miniverse.bountyhunt.respawnInvincibilitySeconds", 120),
            Integer.getInteger("miniverse.bountyhunt.scoreToWin", 5),
            Integer.getInteger("miniverse.bountyhunt.targetSwapIntervalSeconds", 0),
            Boolean.parseBoolean(System.getProperty("miniverse.bountyhunt.trackerEnabled", "true")),
            Boolean.parseBoolean(System.getProperty("miniverse.bountyhunt.netherTracking", "true")),
            Integer.getInteger("miniverse.bountyhunt.compassCooldownSeconds", 2),
            System.getProperty("miniverse.bountyhunt.trackerItemId", "minecraft:compass"),
            modeGrace
        ).normalized();
    }

    public static BountyHuntSettings fromNbt(NbtCompound settings) {
        BountyHuntSettings defaults = defaults();
        if (settings == null || settings.isEmpty()) {
            return defaults;
        }

        return new BountyHuntSettings(
            settings.getInt("gracePeriodSeconds", defaults.gracePeriodSeconds()),
            settings.getInt("respawnInvincibilitySeconds", defaults.respawnInvincibilitySeconds()),
            settings.getInt("scoreToWin", defaults.scoreToWin()),
            settings.getInt("targetSwapIntervalSeconds", defaults.targetSwapIntervalSeconds()),
            settings.getBoolean("trackerEnabled", defaults.trackerEnabled()),
            settings.getBoolean("netherTracking", defaults.netherTrackingEnabled()),
            settings.getInt("compassCooldownSeconds", defaults.compassCooldownSeconds()),
            settings.getString("trackerItemId", defaults.trackerItemId()),
            settings.getInt("disconnectGraceSeconds", defaults.disconnectGraceSeconds())
        ).normalized();
    }

    public static BountyHuntSettings fromProperties(Properties properties) {
        BountyHuntSettings defaults = defaults();
        if (properties == null || properties.isEmpty()) {
            return defaults;
        }

        return new BountyHuntSettings(
            parseInt(properties.getProperty("bountyhunt.gracePeriodSeconds"), defaults.gracePeriodSeconds()),
            parseInt(properties.getProperty("bountyhunt.respawnInvincibilitySeconds"), defaults.respawnInvincibilitySeconds()),
            parseInt(properties.getProperty("bountyhunt.scoreToWin"), defaults.scoreToWin()),
            parseInt(properties.getProperty("bountyhunt.targetSwapIntervalSeconds"), defaults.targetSwapIntervalSeconds()),
            parseBoolean(properties.getProperty("bountyhunt.trackerEnabled"), defaults.trackerEnabled()),
            parseBoolean(properties.getProperty("bountyhunt.netherTracking"), defaults.netherTrackingEnabled()),
            parseInt(properties.getProperty("bountyhunt.compassCooldownSeconds"), defaults.compassCooldownSeconds()),
            properties.getProperty("bountyhunt.trackerItemId", defaults.trackerItemId()),
            parseInt(properties.getProperty("bountyhunt.disconnectGraceSeconds"), defaults.disconnectGraceSeconds())
        ).normalized();
    }
    private BountyHuntSettings normalized() {
        String itemId = this.trackerItemId == null || this.trackerItemId.isBlank()
            ? "minecraft:compass"
            : this.trackerItemId.trim().toLowerCase();
        return new BountyHuntSettings(
            Math.clamp(this.gracePeriodSeconds, 0, 3600),
            Math.clamp(this.respawnInvincibilitySeconds, 0, 3600),
            Math.clamp(this.scoreToWin, 1, 99),
            Math.clamp(this.targetSwapIntervalSeconds, 0, 3600),
            this.trackerEnabled,
            this.netherTrackingEnabled,
            Math.clamp(this.compassCooldownSeconds, 0, 300),
            itemId,
            Math.clamp(this.disconnectGraceSeconds, 0, 3600)
        );
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
    }
}

