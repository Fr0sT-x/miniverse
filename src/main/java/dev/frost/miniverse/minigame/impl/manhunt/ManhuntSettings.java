package dev.frost.miniverse.minigame.impl.manhunt;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.Properties;

public record ManhuntSettings(
    int hunterReleaseDelaySeconds,
    int runnerRespawnDelaySeconds,
    boolean huntersCompassEnabled,
    boolean netherTrackingEnabled,
    int compassCooldownSeconds,
    int runnerGlowPulseMinutes,
    int runnerLives,
    int hunterLives,
    int hunterRespawnDelaySeconds,
    boolean midGameJoinTeleportEnabled,
    int disconnectGraceSeconds,
    boolean runnerRespawnAtTeammate,
    boolean hunterRespawnAtTeammate
) {
    public static final int UNLIMITED_LIVES = -1;

    public static ManhuntSettings defaults() {
        int globalGrace = Integer.getInteger("miniverse.lifecycle.disconnectGraceSeconds", 300);
        int modeGrace = Integer.getInteger("miniverse.manhunt.disconnectGraceSeconds", globalGrace);
        return new ManhuntSettings(
            Integer.getInteger("miniverse.manhunt.hunterReleaseDelaySeconds", 10),
            Integer.getInteger("miniverse.manhunt.runnerRespawnDelaySeconds", 300),
            Boolean.parseBoolean(System.getProperty("miniverse.manhunt.huntersCompass", "true")),
            Boolean.parseBoolean(System.getProperty("miniverse.manhunt.netherTracking", "true")),
            Integer.getInteger("miniverse.manhunt.compassCooldownSeconds", 2),
            Integer.getInteger("miniverse.manhunt.runnerGlowPulseMinutes", 0),
            Integer.getInteger("miniverse.manhunt.runnerLives", UNLIMITED_LIVES),
            Integer.getInteger("miniverse.manhunt.hunterLives", UNLIMITED_LIVES),
            Integer.getInteger("miniverse.manhunt.hunterRespawnDelaySeconds", 5),
            Boolean.parseBoolean(System.getProperty("miniverse.manhunt.midGameJoinTeleportEnabled", "false")),
            modeGrace,
            Boolean.parseBoolean(System.getProperty("miniverse.manhunt.runnerRespawnAtTeammate", "true")),
            Boolean.parseBoolean(System.getProperty("miniverse.manhunt.hunterRespawnAtTeammate", "false"))
        ).normalized();
    }

    public static ManhuntSettings fromNbt(NbtCompound settings) {
        ManhuntSettings defaults = defaults();
        if (settings == null || settings.isEmpty()) {
            return defaults;
        }

        int releaseDelay = getIntOrDefault(settings, "hunterReleaseDelaySeconds", Integer.MIN_VALUE);
        if (releaseDelay == Integer.MIN_VALUE) {
            releaseDelay = getIntOrDefault(settings, "gracePeriodSeconds", defaults.hunterReleaseDelaySeconds());
        }

        int runnerRespawnDelay = getIntOrDefault(settings, "runnerRespawnDelaySeconds", Integer.MIN_VALUE);
        if (runnerRespawnDelay == Integer.MIN_VALUE) {
            runnerRespawnDelay = getIntOrDefault(settings, "speedrunnerRespawnDelaySeconds", Integer.MIN_VALUE);
            if (runnerRespawnDelay == Integer.MIN_VALUE) {
                runnerRespawnDelay = getIntOrDefault(settings, "respawnDelaySeconds", defaults.runnerRespawnDelaySeconds());
            }
        }

        return new ManhuntSettings(
            releaseDelay,
            runnerRespawnDelay,
            getBooleanOrDefault(settings, "huntersCompass", defaults.huntersCompassEnabled()),
            getBooleanOrDefault(settings, "netherTracking", defaults.netherTrackingEnabled()),
            getIntOrDefault(settings, "compassCooldownSeconds", defaults.compassCooldownSeconds()),
            getIntOrDefault(settings, "runnerGlowPulseMinutes", defaults.runnerGlowPulseMinutes()),
            getIntOrDefault(settings, "runnerLives", defaults.runnerLives()),
            getIntOrDefault(settings, "hunterLives", defaults.hunterLives()),
            getIntOrDefault(settings, "hunterRespawnDelaySeconds", defaults.hunterRespawnDelaySeconds()),
            getBooleanOrDefault(settings, "midGameJoinTeleportEnabled", defaults.midGameJoinTeleportEnabled()),
            getIntOrDefault(settings, "disconnectGraceSeconds", defaults.disconnectGraceSeconds()),
            getBooleanOrDefault(settings, "runnerRespawnAtTeammate", defaults.runnerRespawnAtTeammate()),
            getBooleanOrDefault(settings, "hunterRespawnAtTeammate", defaults.hunterRespawnAtTeammate())
        ).normalized();
    }

    public static ManhuntSettings fromProperties(Properties properties) {
        ManhuntSettings defaults = defaults();
        if (properties == null || properties.isEmpty()) {
            return defaults;
        }

        return new ManhuntSettings(
            parseInt(properties.getProperty("manhunt.hunterReleaseDelaySeconds"), defaults.hunterReleaseDelaySeconds()),
            parseInt(properties.getProperty("manhunt.runnerRespawnDelaySeconds", properties.getProperty("manhunt.speedrunnerRespawnDelaySeconds", properties.getProperty("manhunt.respawnDelaySeconds"))), defaults.runnerRespawnDelaySeconds()),
            parseBoolean(properties.getProperty("manhunt.huntersCompass"), defaults.huntersCompassEnabled()),
            parseBoolean(properties.getProperty("manhunt.netherTracking"), defaults.netherTrackingEnabled()),
            parseInt(properties.getProperty("manhunt.compassCooldownSeconds"), defaults.compassCooldownSeconds()),
            parseInt(properties.getProperty("manhunt.runnerGlowPulseMinutes"), defaults.runnerGlowPulseMinutes()),
            parseInt(properties.getProperty("manhunt.runnerLives"), defaults.runnerLives()),
            parseInt(properties.getProperty("manhunt.hunterLives"), defaults.hunterLives()),
            parseInt(properties.getProperty("manhunt.hunterRespawnDelaySeconds"), defaults.hunterRespawnDelaySeconds()),
            parseBoolean(properties.getProperty("manhunt.midGameJoinTeleportEnabled"), defaults.midGameJoinTeleportEnabled()),
            parseInt(properties.getProperty("manhunt.disconnectGraceSeconds"), defaults.disconnectGraceSeconds()),
            parseBoolean(properties.getProperty("manhunt.runnerRespawnAtTeammate"), defaults.runnerRespawnAtTeammate()),
            parseBoolean(properties.getProperty("manhunt.hunterRespawnAtTeammate"), defaults.hunterRespawnAtTeammate())
        ).normalized();
    }

    public ManhuntSettings withRunnerRespawnDelaySeconds(int seconds) {
        return new ManhuntSettings(
            this.hunterReleaseDelaySeconds,
            seconds,
            this.huntersCompassEnabled,
            this.netherTrackingEnabled,
            this.compassCooldownSeconds,
            this.runnerGlowPulseMinutes,
            this.runnerLives,
            this.hunterLives,
            this.hunterRespawnDelaySeconds,
            this.midGameJoinTeleportEnabled,
            this.disconnectGraceSeconds,
            this.runnerRespawnAtTeammate,
            this.hunterRespawnAtTeammate
        ).normalized();
    }

    private ManhuntSettings normalized() {
        return new ManhuntSettings(
            Math.clamp(this.hunterReleaseDelaySeconds, 0, 3600),
            Math.clamp(this.runnerRespawnDelaySeconds, 0, 3600),
            this.huntersCompassEnabled,
            this.netherTrackingEnabled,
            Math.clamp(this.compassCooldownSeconds, 0, 300),
            Math.clamp(this.runnerGlowPulseMinutes, 0, 120),
            normalizeLives(this.runnerLives),
            normalizeLives(this.hunterLives),
            Math.clamp(this.hunterRespawnDelaySeconds, 0, 3600),
            this.midGameJoinTeleportEnabled,
            Math.clamp(this.disconnectGraceSeconds, 0, 3600),
            this.runnerRespawnAtTeammate,
            this.hunterRespawnAtTeammate
        );
    }

    private static int normalizeLives(int lives) {
        return lives < 0 ? UNLIMITED_LIVES : Math.clamp(lives, 1, 100);
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
}
