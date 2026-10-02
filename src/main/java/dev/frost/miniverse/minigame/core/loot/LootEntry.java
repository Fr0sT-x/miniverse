package dev.frost.miniverse.minigame.core.loot;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single lootable item rule with weight, count range, enchantments, and optional potion effects.
 */
public class LootEntry {
    private final ItemConvertible item;
    private final int minCount;
    private final int maxCount;
    private final LootCategory category;
    private final int weight;
    private final Map<RegistryKey<Enchantment>, Integer> enchantments;
    private final RegistryEntry<Potion> potion;
    private final String customName;
    private final List<String> customLore;

    public LootEntry(
        ItemConvertible item,
        int minCount,
        int maxCount,
        LootCategory category,
        int weight,
        Map<RegistryKey<Enchantment>, Integer> enchantments,
        RegistryEntry<Potion> potion,
        String customName,
        List<String> customLore
    ) {
        this.item = item;
        this.minCount = Math.max(1, minCount);
        this.maxCount = Math.max(this.minCount, maxCount);
        this.category = category == null ? LootCategory.MISC : category;
        this.weight = Math.max(1, weight);
        this.enchantments = enchantments == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(enchantments));
        this.potion = potion;
        this.customName = customName;
        this.customLore = customLore == null ? Collections.emptyList() : List.copyOf(customLore);
    }

    public ItemConvertible getItem() {
        return this.item;
    }

    public LootCategory getCategory() {
        return this.category;
    }

    public int getWeight() {
        return this.weight;
    }

    public int getMinCount() {
        return this.minCount;
    }

    public int getMaxCount() {
        return this.maxCount;
    }

    public ItemStack createStack(Random random, RegistryWrapper.WrapperLookup registries) {
        int count = this.minCount == this.maxCount ? this.minCount : this.minCount + random.nextInt(this.maxCount - this.minCount + 1);
        ItemStack stack = new ItemStack(this.item, count);

        if (this.customName != null && !this.customName.isBlank()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(this.customName));
        }

        if (!this.customLore.isEmpty()) {
            List<Text> loreTexts = new ArrayList<>();
            for (String line : this.customLore) {
                loreTexts.add(Text.literal(line));
            }
            stack.set(DataComponentTypes.LORE, new LoreComponent(loreTexts));
        }

        if (this.potion != null) {
            stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(this.potion));
        }

        if (!this.enchantments.isEmpty() && registries != null) {
            registries.getOptionalWrapper(RegistryKeys.ENCHANTMENT).ifPresent(enchRegistry -> {
                for (Map.Entry<RegistryKey<Enchantment>, Integer> entry : this.enchantments.entrySet()) {
                    enchRegistry.getOptional(entry.getKey()).ifPresent(ench -> stack.addEnchantment(ench, entry.getValue()));
                }
            });
        }

        return stack;
    }

    public static Builder builder(ItemConvertible item) {
        return new Builder(item);
    }

    public static class Builder {
        private final ItemConvertible item;
        private int minCount = 1;
        private int maxCount = 1;
        private LootCategory category = LootCategory.MISC;
        private int weight = 10;
        private final Map<RegistryKey<Enchantment>, Integer> enchantments = new LinkedHashMap<>();
        private RegistryEntry<Potion> potion;
        private String customName;
        private final List<String> customLore = new ArrayList<>();

        public Builder(ItemConvertible item) {
            this.item = item;
        }

        public Builder count(int count) {
            this.minCount = count;
            this.maxCount = count;
            return this;
        }

        public Builder count(int min, int max) {
            this.minCount = min;
            this.maxCount = max;
            return this;
        }

        public Builder category(LootCategory category) {
            this.category = category;
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder enchant(RegistryKey<Enchantment> enchantment, int level) {
            this.enchantments.put(enchantment, level);
            return this;
        }

        public Builder potion(RegistryEntry<Potion> potion) {
            this.potion = potion;
            return this;
        }

        public Builder name(String name) {
            this.customName = name;
            return this;
        }

        public Builder lore(String line) {
            this.customLore.add(line);
            return this;
        }

        public LootEntry build() {
            return new LootEntry(this.item, this.minCount, this.maxCount, this.category, this.weight, this.enchantments, this.potion, this.customName, this.customLore);
        }
    }
}
