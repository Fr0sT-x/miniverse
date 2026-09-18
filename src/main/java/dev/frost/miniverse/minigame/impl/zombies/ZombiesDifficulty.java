package dev.frost.miniverse.minigame.impl.zombies;

import net.minecraft.util.Formatting;

public enum ZombiesDifficulty {
    EASY("Easy", 1.0f, 1.0f, 1.0f, 1.0f, 14, 3, 28, 32, 24, 1.0f, Formatting.GREEN),
    NORMAL("Normal", 1.35f, 1.5f, 1.05f, 1.25f, 16, 4, 32, 24, 20, 1.3f, Formatting.GOLD),
    HARD("Hard", 1.8f, 2.0f, 1.10f, 1.55f, 18, 5, 36, 18, 16, 1.6f, Formatting.DARK_RED);

    private final String displayName;
    private final float healthMultiplier;
    private final float damageMultiplier;
    private final double speedMultiplier;
    private final float windowBreakMultiplier;
    private final int baseWaveMobs;
    private final int waveMobsPerRound;
    private final int maxActiveMobs;
    private final int spawnCooldownMin;
    private final int spawnCooldownRandom;
    private final float specialAttackMultiplier;
    private final Formatting color;

    ZombiesDifficulty(
        String displayName,
        float healthMultiplier,
        float damageMultiplier,
        double speedMultiplier,
        float windowBreakMultiplier,
        int baseWaveMobs,
        int waveMobsPerRound,
        int maxActiveMobs,
        int spawnCooldownMin,
        int spawnCooldownRandom,
        float specialAttackMultiplier,
        Formatting color
    ) {
        this.displayName = displayName;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.windowBreakMultiplier = windowBreakMultiplier;
        this.baseWaveMobs = baseWaveMobs;
        this.waveMobsPerRound = waveMobsPerRound;
        this.maxActiveMobs = maxActiveMobs;
        this.spawnCooldownMin = spawnCooldownMin;
        this.spawnCooldownRandom = spawnCooldownRandom;
        this.specialAttackMultiplier = specialAttackMultiplier;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getHealthMultiplier() {
        return healthMultiplier;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public float getWindowBreakMultiplier() {
        return windowBreakMultiplier;
    }

    public int getBaseWaveMobs() {
        return baseWaveMobs;
    }

    public int getWaveMobsPerRound() {
        return waveMobsPerRound;
    }

    public int getMaxActiveMobs() {
        return maxActiveMobs;
    }

    public int getSpawnCooldownMin() {
        return spawnCooldownMin;
    }

    public int getSpawnCooldownRandom() {
        return spawnCooldownRandom;
    }

    public float getSpecialAttackMultiplier() {
        return specialAttackMultiplier;
    }

    public Formatting getColor() {
        return color;
    }

    public ZombiesDifficulty next() {
        ZombiesDifficulty[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
