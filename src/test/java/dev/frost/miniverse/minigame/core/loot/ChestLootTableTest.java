package dev.frost.miniverse.minigame.core.loot;

import net.minecraft.item.ItemConvertible;
import org.junit.Assert;
import org.junit.Test;

public class ChestLootTableTest {

    @Test
    public void testLootTableBuilderAndGuarantees() {
        ItemConvertible sword = () -> null;
        ItemConvertible armor = () -> null;
        ItemConvertible block = () -> null;

        ChestLootTable table = ChestLootTable.builder()
            .rolls(3, 5)
            .guarantee(LootCategory.WEAPON)
            .guarantee(LootCategory.ARMOR)
            .maxCategory(LootCategory.WEAPON, 1)
            .add(LootEntry.builder(sword).category(LootCategory.WEAPON).weight(50).build())
            .add(LootEntry.builder(armor).category(LootCategory.ARMOR).weight(30).build())
            .add(LootEntry.builder(block).count(16, 32).category(LootCategory.BLOCK).weight(40).build())
            .build();

        Assert.assertNotNull(table);
        Assert.assertEquals(3, table.getMinRolls());
        Assert.assertEquals(5, table.getMaxRolls());
        Assert.assertTrue(table.getGuaranteedCategories().contains(LootCategory.WEAPON));
        Assert.assertTrue(table.getGuaranteedCategories().contains(LootCategory.ARMOR));
        Assert.assertFalse(table.getGuaranteedCategories().contains(LootCategory.FOOD));
        Assert.assertEquals(3, table.getEntries().size());
    }

    @Test
    public void testLootEntryBuilder() {
        ItemConvertible dummy = () -> null;
        LootEntry entry = LootEntry.builder(dummy)
            .count(2, 5)
            .category(LootCategory.FOOD)
            .weight(25)
            .build();

        Assert.assertSame(dummy, entry.getItem());
        Assert.assertEquals(2, entry.getMinCount());
        Assert.assertEquals(5, entry.getMaxCount());
        Assert.assertEquals(LootCategory.FOOD, entry.getCategory());
        Assert.assertEquals(25, entry.getWeight());
    }

    @Test
    public void testLootCategoryEnum() {
        Assert.assertEquals(7, LootCategory.values().length);
        Assert.assertEquals(LootCategory.WEAPON, LootCategory.valueOf("WEAPON"));
        Assert.assertEquals(LootCategory.ARMOR, LootCategory.valueOf("ARMOR"));
        Assert.assertEquals(LootCategory.BLOCK, LootCategory.valueOf("BLOCK"));
        Assert.assertEquals(LootCategory.FOOD, LootCategory.valueOf("FOOD"));
        Assert.assertEquals(LootCategory.PROJECTILE, LootCategory.valueOf("PROJECTILE"));
        Assert.assertEquals(LootCategory.UTILITY, LootCategory.valueOf("UTILITY"));
        Assert.assertEquals(LootCategory.MISC, LootCategory.valueOf("MISC"));
    }
}
