package dev.frost.miniverse.minigame.impl.bountyhunt;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.Properties;

public record BountyHuntSettings(
    int gracePeriodSeconds,
    int scoreToWin,
    int targetSwapIntervalSeconds,
    boolean trackerEnabled,
    boolean netherTrackingEnabled,
    int compassCooldownSeconds,
    String trackerItemId,
    int disconnectGraceSeconds,
    boolean highValueTargetEnabled,
    boolean revengeAssignmentEnabled,
    int respawnDelaySeconds
) {
    public static BountyHuntSettings defaults() {
        int globalGrace = Integer.getInteger("miniverse.lifecycle.disconnectGraceSeconds", 300);
        int modeGrace = Integer.getInteger("miniverse.bountyhunt.disconnectGraceSeconds", globalGrace);
        return new BountyHuntSettings(
            Integer.getInteger("miniverse.bountyhunt.gracePeriodSeconds", 300),
            Integer.getInteger("miniverse.bountyhunt.scoreToWin", 5),
            Integer.getInteger("miniverse.bountyhunt.targetSwapIntervalSeconds", 0),
            Boolean.parseBoolean(System.getProperty("miniverse.bountyhunt.trackerEnabled", "true")),
            Boolean.parseBoolean(System.getProperty("miniverse.bountyhunt.netherTracking", "true")),
            Integer.getInteger("miniverse.bountyhunt.compassCooldownSeconds", 2),
            System.getProperty("miniverse.bountyhunt.trackerItemId", "minecraft:compass"),
            modeGrace,
            Boolean.parseBoolean(System.getProperty("miniverse.bountyhunt.highValueTargetEnabled", "false")),
            Boolean.parseBoolean(System.getProperty("miniverse.bountyhunt.revengeAssignmentEnabled", "false")),
            Integer.getInteger("miniverse.bountyhunt.respawnDelaySeconds", 5)
        ).normalized();
    }

    public static BountyHuntSettings fromNbt(NbtCompound settings) {
        BountyHuntSettings defaults = defaults();
        if (settings == null || settings.isEmpty()) {
            return defaults;
        }

        return new BountyHuntSettings(
            getIntOrDefault(settings, "gracePeriodSeconds", defaults.gracePeriodSeconds()),
            getIntOrDefault(settings, "scoreToWin", defaults.scoreToWin()),
            getIntOrDefault(settings, "targetSwapIntervalSeconds", defaults.targetSwapIntervalSeconds()),
            getBooleanOrDefault(settings, "trackerEnabled", defaults.trackerEnabled()),
            getBooleanOrDefault(settings, "netherTracking", defaults.netherTrackingEnabled()),
            getIntOrDefault(settings, "compassCooldownSeconds", defaults.compassCooldownSeconds()),
            getStringOrDefault(settings, "trackerItemId", defaults.trackerItemId()),
            getIntOrDefault(settings, "disconnectGraceSeconds", defaults.disconnectGraceSeconds()),
            getBooleanOrDefault(settings, "highValueTargetEnabled", defaults.highValueTargetEnabled()),
            getBooleanOrDefault(settings, "revengeAssignmentEnabled", defaults.revengeAssignmentEnabled()),
            getIntOrDefault(settings, "respawnDelaySeconds", defaults.respawnDelaySeconds())
        ).normalized();
    }

    public static BountyHuntSettings fromProperties(Properties properties) {
        BountyHuntSettings defaults = defaults();
        if (properties == null || properties.isEmpty()) {
            return defaults;
        }

        return new BountyHuntSettings(
            parseInt(properties.getProperty("bountyhunt.gracePeriodSeconds"), defaults.gracePeriodSeconds()),
            parseInt(properties.getProperty("bountyhunt.scoreToWin"), defaults.scoreToWin()),
            parseInt(properties.getProperty("bountyhunt.targetSwapIntervalSeconds"), defaults.targetSwapIntervalSeconds()),
            parseBoolean(properties.getProperty("bountyhunt.trackerEnabled"), defaults.trackerEnabled()),
            parseBoolean(properties.getProperty("bountyhunt.netherTracking"), defaults.netherTrackingEnabled()),
            parseInt(properties.getProperty("bountyhunt.compassCooldownSeconds"), defaults.compassCooldownSeconds()),
            properties.getProperty("bountyhunt.trackerItemId", defaults.trackerItemId()),
            parseInt(properties.getProperty("bountyhunt.disconnectGraceSeconds"), defaults.disconnectGraceSeconds()),
            parseBoolean(properties.getProperty("bountyhunt.highValueTargetEnabled"), defaults.highValueTargetEnabled()),
            parseBoolean(properties.getProperty("bountyhunt.revengeAssignmentEnabled"), defaults.revengeAssignmentEnabled()),
            parseInt(properties.getProperty("bountyhunt.respawnDelaySeconds"), defaults.respawnDelaySeconds())
        ).normalized();
    }

    public BountyHuntSettings withGracePeriodSeconds(int gracePeriodSeconds) {
        return new BountyHuntSettings(gracePeriodSeconds, scoreToWin, targetSwapIntervalSeconds, trackerEnabled, netherTrackingEnabled, compassCooldownSeconds, trackerItemId, disconnectGraceSeconds, highValueTargetEnabled, revengeAssignmentEnabled, respawnDelaySeconds).normalized();
    }

    public BountyHuntSettings withScoreToWin(int scoreToWin) {
        return new BountyHuntSettings(gracePeriodSeconds, scoreToWin, targetSwapIntervalSeconds, trackerEnabled, netherTrackingEnabled, compassCooldownSeconds, trackerItemId, disconnectGraceSeconds, highValueTargetEnabled, revengeAssignmentEnabled, respawnDelaySeconds).normalized();
    }

    public BountyHuntSettings withTargetSwapIntervalSeconds(int targetSwapIntervalSeconds) {
        return new BountyHuntSettings(gracePeriodSeconds, scoreToWin, targetSwapIntervalSeconds, trackerEnabled, netherTrackingEnabled, compassCooldownSeconds, trackerItemId, disconnectGraceSeconds, highValueTargetEnabled, revengeAssignmentEnabled, respawnDelaySeconds).normalized();
    }

    public BountyHuntSettings withTrackerEnabled(boolean trackerEnabled) {
        return new BountyHuntSettings(gracePeriodSeconds, scoreToWin, targetSwapIntervalSeconds, trackerEnabled, netherTrackingEnabled, compassCooldownSeconds, trackerItemId, disconnectGraceSeconds, highValueTargetEnabled, revengeAssignmentEnabled, respawnDelaySeconds).normalized();
    }

    public BountyHuntSettings withNetherTrackingEnabled(boolean netherTrackingEnabled) {
        return new BountyHuntSettings(gracePeriodSeconds, scoreToWin, targetSwapIntervalSeconds, trackerEnabled, netherTrackingEnabled, compassCooldownSeconds, trackerItemId, disconnectGraceSeconds, highValueTargetEnabled, revengeAssignmentEnabled, respawnDelaySeconds).normalized();
    }

    public BountyHuntSettings withCompassCooldownSeconds(int compassCooldownSeconds) {
        return new BountyHuntSettings(gracePeriodSeconds, scoreToWin, targetSwapIntervalSeconds, trackerEnabled, netherTrackingEnabled, compassCooldownSeconds, trackerItemId, disconnectGraceSeconds, highValueTargetEnabled, revengeAssignmentEnabled, respawnDelaySeconds).normalized();
    }

    public BountyHuntSettings withTrackerItemId(String trackerItemId) {
        return new BountyHuntSettings(gracePeriodSeconds, scoreToWin, targetSwapIntervalSeconds, trackerEnabled, netherTrackingEnabled, compassCooldownSeconds, trackerItemId, disconnectGraceSeconds, highValueTargetEnabled, revengeAssignmentEnabled, respawnDelaySeconds).normalized();
    }

    public BountyHuntSettings withDisconnectGraceSeconds(int disconnectGraceSeconds) {
        return new BountyHuntSettings(gracePeriodSeconds, scoreToWin, targetSwapIntervalSeconds, trackerEnabled, netherTrackingEnabled, compassCooldownSeconds, trackerItemId, disconnectGraceSeconds, highValueTargetEnabled, revengeAssignmentEnabled, respawnDelaySeconds).normalized();
    }

    public BountyHuntSettings withHighValueTargetEnabled(boolean highValueTargetEnabled) {
        return new BountyHuntSettings(gracePeriodSeconds, scoreToWin, targetSwapIntervalSeconds, trackerEnabled, netherTrackingEnabled, compassCooldownSeconds, trackerItemId, disconnectGraceSeconds, highValueTargetEnabled, revengeAssignmentEnabled, respawnDelaySeconds).normalized();
    }

    public BountyHuntSettings withRevengeAssignmentEnabled(boolean revengeAssignmentEnabled) {
        return new BountyHuntSettings(gracePeriodSeconds, scoreToWin, targetSwapIntervalSeconds, trackerEnabled, netherTrackingEnabled, compassCooldownSeconds, trackerItemId, disconnectGraceSeconds, highValueTargetEnabled, revengeAssignmentEnabled, respawnDelaySeconds).normalized();
    }

    private BountyHuntSettings normalized() {
        String itemId = this.trackerItemId == null || this.trackerItemId.isBlank()
            ? "minecraft:compass"
            : this.trackerItemId.trim().toLowerCase();
        return new BountyHuntSettings(
            Math.clamp(this.gracePeriodSeconds, 0, 3600),
            Math.clamp(this.scoreToWin, 1, 99),
            Math.clamp(this.targetSwapIntervalSeconds, 0, 3600),
            this.trackerEnabled,
            this.netherTrackingEnabled,
            Math.clamp(this.compassCooldownSeconds, 0, 300),
            itemId,
            Math.clamp(this.disconnectGraceSeconds, 0, 3600),
            this.highValueTargetEnabled,
            this.revengeAssignmentEnabled,
            this.respawnDelaySeconds
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

    private static int getIntOrDefault(NbtCompound settings, String key, int fallback) {
        return settings != null && settings.contains(key, NbtElement.NUMBER_TYPE)
            ? settings.getInt(key)
            : fallback;
    }

    private static boolean getBooleanOrDefault(NbtCompound settings, String key, boolean fallback) {
        return settings != null && settings.contains(key, NbtElement.NUMBER_TYPE)
            ? settings.getBoolean(key)
            : fallback;
    }

    private static String getStringOrDefault(NbtCompound settings, String key, String fallback) {
        return settings != null && settings.contains(key, NbtElement.STRING_TYPE)
            ? settings.getString(key)
            : fallback;
    }
}
