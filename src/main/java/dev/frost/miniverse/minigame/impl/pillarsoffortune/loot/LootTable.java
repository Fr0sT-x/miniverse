package dev.frost.miniverse.minigame.impl.pillarsoffortune.loot;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

public class LootTable {
    private final List<LootEntry> entries = new ArrayList<>();
    
    public void addEntry(Item item, int min, int max, int weight) {
        this.entries.add(new LootEntry(item, min, max, weight));
    }
    
    public List<ItemStack> roll(Random random, int rolls) {
        List<ItemStack> result = new ArrayList<>();
        int totalWeight = this.entries.stream().mapToInt(LootEntry::weight).sum();
        
        if (totalWeight <= 0) {
            return result;
        }
        
        for (int i = 0; i < rolls; i++) {
            int r = random.nextInt(totalWeight);
            int current = 0;
            for (LootEntry entry : this.entries) {
                current += entry.weight();
                if (r < current) {
                    int count = entry.min() + (entry.min() == entry.max() ? 0 : random.nextInt(entry.max() - entry.min() + 1));
                    result.add(new ItemStack(entry.item(), count));
                    break;
                }
            }
        }
        return result;
    }
    
    public static LootTable createDefault() {
        LootTable table = new LootTable();
        // Mimic typical Fortune Pillars loot
        table.addEntry(Items.WOODEN_SWORD, 1, 1, 15);
        table.addEntry(Items.STONE_SWORD, 1, 1, 10);
        table.addEntry(Items.BOW, 1, 1, 10);
        table.addEntry(Items.ARROW, 2, 8, 15);
        table.addEntry(Items.GOLDEN_APPLE, 1, 2, 5);
        table.addEntry(Items.COOKED_BEEF, 2, 5, 20);
        table.addEntry(Items.COBBLESTONE, 16, 32, 20);
        table.addEntry(Items.WATER_BUCKET, 1, 1, 10);
        table.addEntry(Items.LAVA_BUCKET, 1, 1, 5);
        table.addEntry(Items.LEATHER_HELMET, 1, 1, 10);
        table.addEntry(Items.LEATHER_CHESTPLATE, 1, 1, 10);
        table.addEntry(Items.LEATHER_LEGGINGS, 1, 1, 10);
        table.addEntry(Items.LEATHER_BOOTS, 1, 1, 10);
        table.addEntry(Items.IRON_HELMET, 1, 1, 5);
        table.addEntry(Items.IRON_CHESTPLATE, 1, 1, 5);
        return table;
    }
    
    private record LootEntry(Item item, int min, int max, int weight) {}
}
