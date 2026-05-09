package dev.frost.miniverse.minigame.impl.bountyhunt;

import net.minecraft.nbt.NbtCompound;

public record BountyHuntSettings(
    int gracePeriodSeconds,
    int respawnInvincibilitySeconds,
    int scoreToWin,
    int targetSwapIntervalSeconds,
    boolean trackerEnabled,
    boolean netherTrackingEnabled,
    int compassCooldownSeconds,
    String trackerItemId
) {
    public static BountyHuntSettings defaults() {
        return new BountyHuntSettings(
            Integer.getInteger("miniverse.bountyhunt.gracePeriodSeconds", 300),
            Integer.getInteger("miniverse.bountyhunt.respawnInvincibilitySeconds", 120),
            Integer.getInteger("miniverse.bountyhunt.scoreToWin", 5),
            Integer.getInteger("miniverse.bountyhunt.targetSwapIntervalSeconds", 0),
            Boolean.parseBoolean(System.getProperty("miniverse.bountyhunt.trackerEnabled", "true")),
            Boolean.parseBoolean(System.getProperty("miniverse.bountyhunt.netherTracking", "true")),
            Integer.getInteger("miniverse.bountyhunt.compassCooldownSeconds", 2),
            System.getProperty("miniverse.bountyhunt.trackerItemId", "minecraft:compass")
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
            settings.getString("trackerItemId", defaults.trackerItemId())
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
            itemId
        );
    }
}

