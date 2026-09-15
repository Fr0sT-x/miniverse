package dev.frost.miniverse.minigame.impl.horde.wave;

import java.util.Random;

public record WaveDefinition(
    int waveNumber,
    int concurrentMobCap,
    double minerChance,
    double harpoonChance,
    double sapperChance,
    double creeperChance,
    double skeletonChance,
    double spiderChance,
    MilestoneType milestone
) {
    public enum MilestoneType {
        NONE,
        BLOOD_MOON,     // Wave 5
        SHADOW_PLAGUE,  // Wave 10
        APOCALYPSE      // Wave 15
    }

    public static WaveDefinition computeFor(int wave, int playerCount) {
        int scaledPlayers = Math.max(1, playerCount);
        int mobCap = Math.min(45, 6 + wave + (scaledPlayers - 1) * 4);

        double minerChance = wave >= 3 ? Math.min(0.15, 0.05 + (wave * 0.01)) : 0.0;
        double harpoonChance = wave >= 4 ? Math.min(0.12, 0.04 + (wave * 0.008)) : 0.0;
        double sapperChance = wave >= 5 ? Math.min(0.10, 0.03 + (wave * 0.007)) : 0.0;
        double creeperChance = wave >= 2 ? Math.min(0.15, 0.05 + (wave * 0.01)) : 0.0;
        double skeletonChance = Math.min(0.25, 0.15 + (wave * 0.01));
        double spiderChance = Math.min(0.20, 0.15 + (wave * 0.01));

        MilestoneType milestone = MilestoneType.NONE;
        if (wave == 5) milestone = MilestoneType.BLOOD_MOON;
        else if (wave == 10) milestone = MilestoneType.SHADOW_PLAGUE;
        else if (wave == 15) milestone = MilestoneType.APOCALYPSE;

        return new WaveDefinition(
            wave,
            mobCap,
            minerChance,
            harpoonChance,
            sapperChance,
            creeperChance,
            skeletonChance,
            spiderChance,
            milestone
        );
    }

    public WaveEngine.SpawnType pickSpawnType(Random random) {
        double roll = random.nextDouble();
        double cumulative = 0.0;

        cumulative += sapperChance;
        if (roll < cumulative) return WaveEngine.SpawnType.SAPPER_CREEPER;

        cumulative += minerChance;
        if (roll < cumulative) return WaveEngine.SpawnType.MINER_ZOMBIE;

        cumulative += harpoonChance;
        if (roll < cumulative) return WaveEngine.SpawnType.HARPOON_DROWNED;

        cumulative += creeperChance;
        if (roll < cumulative) return WaveEngine.SpawnType.CREEPER;

        cumulative += skeletonChance;
        if (roll < cumulative) return WaveEngine.SpawnType.SKELETON;

        cumulative += spiderChance;
        if (roll < cumulative) return WaveEngine.SpawnType.SPIDER;

        return WaveEngine.SpawnType.ZOMBIE;
    }
}
