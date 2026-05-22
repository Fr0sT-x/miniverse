package dev.frost.miniverse.minigame.impl.manhunt;

import net.minecraft.nbt.NbtCompound;

import java.util.Properties;

public record ManhuntSettings(
    int hunterReleaseDelaySeconds,
    int speedrunnerRespawnDelaySeconds,
    boolean huntersCompassEnabled,
    boolean netherTrackingEnabled,
    int compassCooldownSeconds,
    int runnerGlowPulseMinutes,
    int runnerLives,
    int hunterLives,
    int hunterRespawnDelaySeconds,
    int disconnectGraceSeconds
) {
    public static final int UNLIMITED_LIVES = -1;

    public static ManhuntSettings defaults() {
        int globalGrace = Integer.getInteger("miniverse.lifecycle.disconnectGraceSeconds", 300);
        int modeGrace = Integer.getInteger("miniverse.manhunt.disconnectGraceSeconds", globalGrace);
        return new ManhuntSettings(
            Integer.getInteger("miniverse.manhunt.hunterReleaseDelaySeconds", 10),
            Integer.getInteger("miniverse.manhunt.respawnDelaySeconds", ManhuntSpeedrunnerRespawnSystem.DEFAULT_RESPAWN_DELAY_SECONDS),
            Boolean.parseBoolean(System.getProperty("miniverse.manhunt.huntersCompass", "true")),
            Boolean.parseBoolean(System.getProperty("miniverse.manhunt.netherTracking", "true")),
            Integer.getInteger("miniverse.manhunt.compassCooldownSeconds", 2),
            Integer.getInteger("miniverse.manhunt.runnerGlowPulseMinutes", 0),
            Integer.getInteger("miniverse.manhunt.runnerLives", UNLIMITED_LIVES),
            Integer.getInteger("miniverse.manhunt.hunterLives", UNLIMITED_LIVES),
            Integer.getInteger("miniverse.manhunt.hunterRespawnDelaySeconds", 0),
            modeGrace
        ).normalized();
    }

    public static ManhuntSettings fromNbt(NbtCompound settings) {
        ManhuntSettings defaults = defaults();
        if (settings == null || settings.isEmpty()) {
            return defaults;
        }

        int releaseDelay = settings.getInt("hunterReleaseDelaySeconds")
            .orElseGet(() -> settings.getInt("gracePeriodSeconds").orElse(defaults.hunterReleaseDelaySeconds()));

        return new ManhuntSettings(
            releaseDelay,
            settings.getInt("speedrunnerRespawnDelaySeconds", defaults.speedrunnerRespawnDelaySeconds()),
            settings.getBoolean("huntersCompass", defaults.huntersCompassEnabled()),
            settings.getBoolean("netherTracking", defaults.netherTrackingEnabled()),
            settings.getInt("compassCooldownSeconds", defaults.compassCooldownSeconds()),
            settings.getInt("runnerGlowPulseMinutes", defaults.runnerGlowPulseMinutes()),
            settings.getInt("runnerLives", defaults.runnerLives()),
            settings.getInt("hunterLives", defaults.hunterLives()),
            settings.getInt("hunterRespawnDelaySeconds", defaults.hunterRespawnDelaySeconds()),
            settings.getInt("disconnectGraceSeconds", defaults.disconnectGraceSeconds())
        ).normalized();
    }

    public static ManhuntSettings fromProperties(Properties properties) {
        ManhuntSettings defaults = defaults();
        if (properties == null || properties.isEmpty()) {
            return defaults;
        }

        return new ManhuntSettings(
            parseInt(properties.getProperty("manhunt.hunterReleaseDelaySeconds"), defaults.hunterReleaseDelaySeconds()),
            parseInt(properties.getProperty("manhunt.speedrunnerRespawnDelaySeconds"), defaults.speedrunnerRespawnDelaySeconds()),
            parseBoolean(properties.getProperty("manhunt.huntersCompass"), defaults.huntersCompassEnabled()),
            parseBoolean(properties.getProperty("manhunt.netherTracking"), defaults.netherTrackingEnabled()),
            parseInt(properties.getProperty("manhunt.compassCooldownSeconds"), defaults.compassCooldownSeconds()),
            parseInt(properties.getProperty("manhunt.runnerGlowPulseMinutes"), defaults.runnerGlowPulseMinutes()),
            parseInt(properties.getProperty("manhunt.runnerLives"), defaults.runnerLives()),
            parseInt(properties.getProperty("manhunt.hunterLives"), defaults.hunterLives()),
            parseInt(properties.getProperty("manhunt.hunterRespawnDelaySeconds"), defaults.hunterRespawnDelaySeconds()),
            parseInt(properties.getProperty("manhunt.disconnectGraceSeconds"), defaults.disconnectGraceSeconds())
        ).normalized();
    }

    public ManhuntSettings withSpeedrunnerRespawnDelaySeconds(int seconds) {
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
            this.disconnectGraceSeconds
        ).normalized();
    }

    private ManhuntSettings normalized() {
        return new ManhuntSettings(
            Math.clamp(this.hunterReleaseDelaySeconds, 0, 3600),
            Math.clamp(this.speedrunnerRespawnDelaySeconds, 0, 3600),
            this.huntersCompassEnabled,
            this.netherTrackingEnabled,
            Math.clamp(this.compassCooldownSeconds, 0, 300),
            Math.clamp(this.runnerGlowPulseMinutes, 0, 120),
            normalizeLives(this.runnerLives),
            normalizeLives(this.hunterLives),
            Math.clamp(this.hunterRespawnDelaySeconds, 0, 3600),
            Math.clamp(this.disconnectGraceSeconds, 0, 3600)
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
}
