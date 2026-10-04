package dev.frost.miniverse.minigame.core.loot;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A data-driven chest loot table supporting weighted random selection,
 * category guarantees (ensuring players always get blocks, weapons, armor, etc.),
 * and category caps to avoid broken rolls.
 */
public class ChestLootTable {
    private final List<LootEntry> entries;
    private final int minRolls;
    private final int maxRolls;
    private final Set<LootCategory> guaranteedCategories;
    private final Map<LootCategory, Integer> maxPerCategory;

    public ChestLootTable(
        List<LootEntry> entries,
        int minRolls,
        int maxRolls,
        Set<LootCategory> guaranteedCategories,
        Map<LootCategory, Integer> maxPerCategory
    ) {
        this.entries = entries == null ? Collections.emptyList() : List.copyOf(entries);
        this.minRolls = Math.max(1, minRolls);
        this.maxRolls = Math.max(this.minRolls, maxRolls);
        this.guaranteedCategories = guaranteedCategories == null ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(guaranteedCategories));
        this.maxPerCategory = maxPerCategory == null ? Collections.emptyMap() : Collections.unmodifiableMap(new EnumMap<>(maxPerCategory));
    }

    public List<ItemStack> roll(Random random, RegistryWrapper.WrapperLookup registries) {
        List<ItemStack> generated = new ArrayList<>();
        Map<LootCategory, Integer> categoryCounts = new EnumMap<>(LootCategory.class);

        int targetRolls = this.minRolls == this.maxRolls
            ? this.minRolls
            : this.minRolls + random.nextInt(this.maxRolls - this.minRolls + 1);

        // 1. Fulfill guaranteed categories first
        for (LootCategory cat : this.guaranteedCategories) {
            List<LootEntry> catEntries = this.getEntriesForCategory(cat);
            if (!catEntries.isEmpty()) {
                LootEntry picked = this.selectWeighted(catEntries, random);
                if (picked != null) {
                    generated.add(picked.createStack(random, registries));
                    categoryCounts.merge(cat, 1, Integer::sum);
                }
            }
        }

        // 2. Fill remaining rolls from the general pool respecting category caps
        int attempts = 0;
        while (generated.size() < targetRolls && attempts < targetRolls * 4) {
            attempts++;
            List<LootEntry> eligibleEntries = new ArrayList<>();
            for (LootEntry entry : this.entries) {
                int current = categoryCounts.getOrDefault(entry.getCategory(), 0);
                int maxAllowed = this.maxPerCategory.getOrDefault(entry.getCategory(), Integer.MAX_VALUE);
                if (current < maxAllowed) {
                    eligibleEntries.add(entry);
                }
            }

            if (eligibleEntries.isEmpty()) {
                break;
            }

            LootEntry picked = this.selectWeighted(eligibleEntries, random);
            if (picked != null) {
                generated.add(picked.createStack(random, registries));
                categoryCounts.merge(picked.getCategory(), 1, Integer::sum);
            }
        }

        return generated;
    }

    private List<LootEntry> getEntriesForCategory(LootCategory category) {
        List<LootEntry> list = new ArrayList<>();
        for (LootEntry entry : this.entries) {
            if (entry.getCategory() == category) {
                list.add(entry);
            }
        }
        return list;
    }

    private LootEntry selectWeighted(List<LootEntry> pool, Random random) {
        if (pool.isEmpty()) return null;
        int totalWeight = 0;
        for (LootEntry entry : pool) {
            totalWeight += entry.getWeight();
        }
        if (totalWeight <= 0) return pool.get(random.nextInt(pool.size()));

        int roll = random.nextInt(totalWeight);
        int current = 0;
        for (LootEntry entry : pool) {
            current += entry.getWeight();
            if (roll < current) {
                return entry;
            }
        }
        return pool.getLast();
    }

    public Set<LootCategory> getGuaranteedCategories() {
        return this.guaranteedCategories;
    }

    public List<LootEntry> getEntries() {
        return this.entries;
    }

    public int getMinRolls() {
        return this.minRolls;
    }

    public int getMaxRolls() {
        return this.maxRolls;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<LootEntry> entries = new ArrayList<>();
        private int minRolls = 4;
        private int maxRolls = 6;
        private final Set<LootCategory> guaranteedCategories = EnumSet.noneOf(LootCategory.class);
        private final Map<LootCategory, Integer> maxPerCategory = new EnumMap<>(LootCategory.class);

        public Builder rolls(int min, int max) {
            this.minRolls = min;
            this.maxRolls = max;
            return this;
        }

        public Builder add(LootEntry entry) {
            this.entries.add(entry);
            return this;
        }

        public Builder guarantee(LootCategory category) {
            this.guaranteedCategories.add(category);
            return this;
        }

        public Builder maxCategory(LootCategory category, int max) {
            this.maxPerCategory.put(category, max);
            return this;
        }

        public ChestLootTable build() {
            return new ChestLootTable(this.entries, this.minRolls, this.maxRolls, this.guaranteedCategories, this.maxPerCategory);
        }
    }
}
