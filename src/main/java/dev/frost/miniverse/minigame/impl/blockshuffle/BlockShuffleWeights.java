package dev.frost.miniverse.minigame.impl.blockshuffle;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class BlockShuffleWeights {
    public static final Map<Identifier, Integer> WEIGHTS = new HashMap<>();

    public static final Set<Identifier> EASY_POOL = Set.of(
        Identifier.of("minecraft:dirt"),
        Identifier.of("minecraft:grass_block"),
        Identifier.of("minecraft:stone"),
        Identifier.of("minecraft:cobblestone"),
        Identifier.of("minecraft:oak_log"),
        Identifier.of("minecraft:sand"),
        Identifier.of("minecraft:gravel")
    );

    public static final Set<Identifier> STANDARD_POOL = new java.util.HashSet<>(EASY_POOL);
    static {
        STANDARD_POOL.addAll(Set.of(
            Identifier.of("minecraft:iron_ore"),
            Identifier.of("minecraft:coal_ore"),
            Identifier.of("minecraft:netherrack"),
            Identifier.of("minecraft:end_stone"),
            Identifier.of("minecraft:glass"),
            Identifier.of("minecraft:crafting_table"),
            Identifier.of("minecraft:furnace")
        ));
    }

    public static final Set<Identifier> HARDCORE_POOL = Set.of(
        Identifier.of("minecraft:ancient_debris"),
        Identifier.of("minecraft:diamond_block"),
        Identifier.of("minecraft:obsidian"),
        Identifier.of("minecraft:crying_obsidian"),
        Identifier.of("minecraft:emerald_block"),
        Identifier.of("minecraft:netherite_block"),
        Identifier.of("minecraft:beacon"),
        Identifier.of("minecraft:dragon_egg")
    );

    static {
        // High weights (Common)
        for (Identifier id : EASY_POOL) {
            WEIGHTS.put(id, 20);
        }
        
        // Medium weights
        WEIGHTS.put(Identifier.of("minecraft:iron_ore"), 10);
        WEIGHTS.put(Identifier.of("minecraft:coal_ore"), 10);
        WEIGHTS.put(Identifier.of("minecraft:glass"), 10);
        WEIGHTS.put(Identifier.of("minecraft:crafting_table"), 10);
        WEIGHTS.put(Identifier.of("minecraft:furnace"), 10);

        // Low weights (Rare)
        for (Identifier id : HARDCORE_POOL) {
            WEIGHTS.put(id, 1);
        }
    }

    public static int getWeight(Identifier id) {
        return WEIGHTS.getOrDefault(id, 5);
    }

    public static Identifier pickRandomBlock(Set<Identifier> pool) {
        if (pool == null || pool.isEmpty()) {
            return Identifier.of("minecraft:grass_block");
        }

        int totalWeight = 0;
        for (Identifier id : pool) {
            totalWeight += getWeight(id);
        }

        int randomVal = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;

        for (Identifier id : pool) {
            currentWeight += getWeight(id);
            if (randomVal < currentWeight) {
                return id;
            }
        }

        return pool.iterator().next(); // Fallback
    }

    public static Identifier pickHardRandomBlock(Set<Identifier> pool) {
        // For sudden death, bias heavily towards harder blocks by inversing weights.
        if (pool == null || pool.isEmpty()) {
            return Identifier.of("minecraft:grass_block");
        }

        int totalWeight = 0;
        for (Identifier id : pool) {
            totalWeight += (30 - getWeight(id)); // Max weight is ~20, so inverse is larger for rare blocks
        }

        if (totalWeight <= 0) return pool.iterator().next();

        int randomVal = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;

        for (Identifier id : pool) {
            currentWeight += (30 - getWeight(id));
            if (randomVal < currentWeight) {
                return id;
            }
        }

        return pool.iterator().next();
    }
}
