package dev.frost.miniverse.minigame.impl.hideandseek;

import net.minecraft.nbt.NbtCompound;
import java.util.Properties;

public record HideAndSeekSettings(
    int hidingTimeSeconds,
    int gameDurationSeconds,
    int seekerCount,
    double solidifyDelaySeconds,
    float seekerMissPenalty,
    int tauntCooldownSeconds,
    int sonarUnlockSeconds,
    int passiveSoundIntervalSeconds,
    int passiveSoundLateGameSeconds,
    int hotbarCycleSeconds,
    float seekerPassiveRegenCap
) {
    public static final int DEFAULT_HIDING_TIME = 30;
    public static final int DEFAULT_GAME_DURATION = 300; // 5 minutes
    public static final int DEFAULT_SEEKER_COUNT = 1;
    public static final double DEFAULT_SOLIDIFY_DELAY = 2.5;
    public static final float DEFAULT_SEEKER_MISS_PENALTY = 1.0F; // 0.5 hearts
    public static final int DEFAULT_TAUNT_COOLDOWN = 20;
    public static final int DEFAULT_SONAR_UNLOCK = 60; // Final 60 seconds
    public static final int DEFAULT_PASSIVE_SOUND_INTERVAL = 30;
    public static final int DEFAULT_PASSIVE_SOUND_LATE_GAME = 15;
    public static final int DEFAULT_HOTBAR_CYCLE_SECONDS = 3;
    public static final float DEFAULT_PASSIVE_REGEN_CAP = 10.0F; // 5 hearts (10 HP)

    public HideAndSeekSettings(
        int hidingTimeSeconds,
        int gameDurationSeconds,
        int seekerCount,
        double solidifyDelaySeconds,
        float seekerMissPenalty,
        int tauntCooldownSeconds,
        int sonarUnlockSeconds
    ) {
        this(
            hidingTimeSeconds,
            gameDurationSeconds,
            seekerCount,
            solidifyDelaySeconds,
            seekerMissPenalty,
            tauntCooldownSeconds,
            sonarUnlockSeconds,
            DEFAULT_PASSIVE_SOUND_INTERVAL,
            DEFAULT_PASSIVE_SOUND_LATE_GAME,
            DEFAULT_HOTBAR_CYCLE_SECONDS,
            DEFAULT_PASSIVE_REGEN_CAP
        );
    }

    public HideAndSeekSettings(
        int hidingTimeSeconds,
        int gameDurationSeconds,
        int seekerCount,
        double solidifyDelaySeconds,
        float seekerMissPenalty,
        int tauntCooldownSeconds,
        int sonarUnlockSeconds,
        int passiveSoundIntervalSeconds,
        int passiveSoundLateGameSeconds,
        int hotbarCycleSeconds
    ) {
        this(
            hidingTimeSeconds,
            gameDurationSeconds,
            seekerCount,
            solidifyDelaySeconds,
            seekerMissPenalty,
            tauntCooldownSeconds,
            sonarUnlockSeconds,
            passiveSoundIntervalSeconds,
            passiveSoundLateGameSeconds,
            hotbarCycleSeconds,
            DEFAULT_PASSIVE_REGEN_CAP
        );
    }

    public static HideAndSeekSettings defaults() {
        return new HideAndSeekSettings(
            DEFAULT_HIDING_TIME,
            DEFAULT_GAME_DURATION,
            DEFAULT_SEEKER_COUNT,
            DEFAULT_SOLIDIFY_DELAY,
            DEFAULT_SEEKER_MISS_PENALTY,
            DEFAULT_TAUNT_COOLDOWN,
            DEFAULT_SONAR_UNLOCK,
            DEFAULT_PASSIVE_SOUND_INTERVAL,
            DEFAULT_PASSIVE_SOUND_LATE_GAME,
            DEFAULT_HOTBAR_CYCLE_SECONDS,
            DEFAULT_PASSIVE_REGEN_CAP
        );
    }

    public static HideAndSeekSettings fromNbt(NbtCompound nbt) {
        if (nbt == null || nbt.isEmpty()) {
            return defaults();
        }
        int hidingTime = nbt.contains("hidingTimeSeconds") ? nbt.getInt("hidingTimeSeconds") : DEFAULT_HIDING_TIME;
        int gameDuration = nbt.contains("gameDurationSeconds") ? nbt.getInt("gameDurationSeconds") : DEFAULT_GAME_DURATION;
        int seekerCount = nbt.contains("seekerCount") ? nbt.getInt("seekerCount") : DEFAULT_SEEKER_COUNT;
        double solidifyDelay = nbt.contains("solidifyDelaySeconds") ? nbt.getDouble("solidifyDelaySeconds") : DEFAULT_SOLIDIFY_DELAY;
        float missPenalty = nbt.contains("seekerMissPenalty") ? nbt.getFloat("seekerMissPenalty") : DEFAULT_SEEKER_MISS_PENALTY;
        int tauntCooldown = nbt.contains("tauntCooldownSeconds") ? nbt.getInt("tauntCooldownSeconds") : DEFAULT_TAUNT_COOLDOWN;
        int sonarUnlock = nbt.contains("sonarUnlockSeconds") ? nbt.getInt("sonarUnlockSeconds") : DEFAULT_SONAR_UNLOCK;
        int passiveSoundInterval = nbt.contains("passiveSoundIntervalSeconds") ? nbt.getInt("passiveSoundIntervalSeconds") : DEFAULT_PASSIVE_SOUND_INTERVAL;
        int passiveSoundLateGame = nbt.contains("passiveSoundLateGameSeconds") ? nbt.getInt("passiveSoundLateGameSeconds") : DEFAULT_PASSIVE_SOUND_LATE_GAME;
        int hotbarCycle = nbt.contains("hotbarCycleSeconds") ? nbt.getInt("hotbarCycleSeconds") : DEFAULT_HOTBAR_CYCLE_SECONDS;
        float passiveRegenCap = nbt.contains("seekerPassiveRegenCap") ? nbt.getFloat("seekerPassiveRegenCap") : DEFAULT_PASSIVE_REGEN_CAP;
        return new HideAndSeekSettings(hidingTime, gameDuration, seekerCount, solidifyDelay, missPenalty, tauntCooldown, sonarUnlock, passiveSoundInterval, passiveSoundLateGame, hotbarCycle, passiveRegenCap);
    }

    public static HideAndSeekSettings fromProperties(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return defaults();
        }
        int hidingTime = parseInt(properties.getProperty("hideandseek.hidingTimeSeconds"), DEFAULT_HIDING_TIME);
        int gameDuration = parseInt(properties.getProperty("hideandseek.gameDurationSeconds"), DEFAULT_GAME_DURATION);
        int seekerCount = parseInt(properties.getProperty("hideandseek.seekerCount"), DEFAULT_SEEKER_COUNT);
        double solidifyDelay = parseDouble(properties.getProperty("hideandseek.solidifyDelaySeconds"), DEFAULT_SOLIDIFY_DELAY);
        float missPenalty = parseFloat(properties.getProperty("hideandseek.seekerMissPenalty"), DEFAULT_SEEKER_MISS_PENALTY);
        int tauntCooldown = parseInt(properties.getProperty("hideandseek.tauntCooldownSeconds"), DEFAULT_TAUNT_COOLDOWN);
        int sonarUnlock = parseInt(properties.getProperty("hideandseek.sonarUnlockSeconds"), DEFAULT_SONAR_UNLOCK);
        int passiveSoundInterval = parseInt(properties.getProperty("hideandseek.passiveSoundIntervalSeconds"), DEFAULT_PASSIVE_SOUND_INTERVAL);
        int passiveSoundLateGame = parseInt(properties.getProperty("hideandseek.passiveSoundLateGameSeconds"), DEFAULT_PASSIVE_SOUND_LATE_GAME);
        int hotbarCycle = parseInt(properties.getProperty("hideandseek.hotbarCycleSeconds"), DEFAULT_HOTBAR_CYCLE_SECONDS);
        float passiveRegenCap = parseFloat(properties.getProperty("hideandseek.seekerPassiveRegenCap"), DEFAULT_PASSIVE_REGEN_CAP);
        return new HideAndSeekSettings(hidingTime, gameDuration, seekerCount, solidifyDelay, missPenalty, tauntCooldown, sonarUnlock, passiveSoundInterval, passiveSoundLateGame, hotbarCycle, passiveRegenCap);
    }

    public void writeTo(Properties properties) {
        properties.setProperty("hideandseek.hidingTimeSeconds", String.valueOf(hidingTimeSeconds));
        properties.setProperty("hideandseek.gameDurationSeconds", String.valueOf(gameDurationSeconds));
        properties.setProperty("hideandseek.seekerCount", String.valueOf(seekerCount));
        properties.setProperty("hideandseek.solidifyDelaySeconds", String.valueOf(solidifyDelaySeconds));
        properties.setProperty("hideandseek.seekerMissPenalty", String.valueOf(seekerMissPenalty));
        properties.setProperty("hideandseek.tauntCooldownSeconds", String.valueOf(tauntCooldownSeconds));
        properties.setProperty("hideandseek.sonarUnlockSeconds", String.valueOf(sonarUnlockSeconds));
        properties.setProperty("hideandseek.passiveSoundIntervalSeconds", String.valueOf(passiveSoundIntervalSeconds));
        properties.setProperty("hideandseek.passiveSoundLateGameSeconds", String.valueOf(passiveSoundLateGameSeconds));
        properties.setProperty("hideandseek.hotbarCycleSeconds", String.valueOf(hotbarCycleSeconds));
        properties.setProperty("hideandseek.seekerPassiveRegenCap", String.valueOf(seekerPassiveRegenCap));
    }

    public void writeTo(NbtCompound nbt) {
        nbt.putInt("hidingTimeSeconds", hidingTimeSeconds);
        nbt.putInt("gameDurationSeconds", gameDurationSeconds);
        nbt.putInt("seekerCount", seekerCount);
        nbt.putDouble("solidifyDelaySeconds", solidifyDelaySeconds);
        nbt.putFloat("seekerMissPenalty", seekerMissPenalty);
        nbt.putInt("tauntCooldownSeconds", tauntCooldownSeconds);
        nbt.putInt("sonarUnlockSeconds", sonarUnlockSeconds);
        nbt.putInt("passiveSoundIntervalSeconds", passiveSoundIntervalSeconds);
        nbt.putInt("passiveSoundLateGameSeconds", passiveSoundLateGameSeconds);
        nbt.putInt("hotbarCycleSeconds", hotbarCycleSeconds);
        nbt.putFloat("seekerPassiveRegenCap", seekerPassiveRegenCap);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
