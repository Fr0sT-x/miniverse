package dev.frost.miniverse.minigame.core.loot;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;

/**
 * Pre-configured competitive loot tables for Skywars modes (Normal & Insane),
 * covering both Island/Spawn chests and Mid/Center Feast chests.
 */
public final class SkywarsLootTables {
    private SkywarsLootTables() {
    }

    public static ChestLootTable createIslandNormal() {
        return ChestLootTable.builder()
            .rolls(4, 6)
            .guarantee(LootCategory.WEAPON)
            .guarantee(LootCategory.BLOCK)
            .guarantee(LootCategory.ARMOR)
            .guarantee(LootCategory.FOOD)
            .maxCategory(LootCategory.WEAPON, 2)
            .maxCategory(LootCategory.ARMOR, 2)
            // Weapons
            .add(LootEntry.builder(Items.STONE_SWORD).category(LootCategory.WEAPON).weight(35).build())
            .add(LootEntry.builder(Items.IRON_SWORD).category(LootCategory.WEAPON).weight(20).build())
            .add(LootEntry.builder(Items.STONE_AXE).category(LootCategory.WEAPON).weight(15).build())
            .add(LootEntry.builder(Items.IRON_AXE).category(LootCategory.WEAPON).weight(10).build())
            // Armor
            .add(LootEntry.builder(Items.IRON_HELMET).category(LootCategory.ARMOR).weight(20).build())
            .add(LootEntry.builder(Items.IRON_CHESTPLATE).category(LootCategory.ARMOR).weight(12).build())
            .add(LootEntry.builder(Items.IRON_LEGGINGS).category(LootCategory.ARMOR).weight(15).build())
            .add(LootEntry.builder(Items.IRON_BOOTS).category(LootCategory.ARMOR).weight(20).build())
            .add(LootEntry.builder(Items.CHAINMAIL_CHESTPLATE).category(LootCategory.ARMOR).weight(15).build())
            .add(LootEntry.builder(Items.CHAINMAIL_LEGGINGS).category(LootCategory.ARMOR).weight(15).build())
            // Blocks
            .add(LootEntry.builder(Items.OAK_PLANKS).count(32, 64).category(LootCategory.BLOCK).weight(40).build())
            .add(LootEntry.builder(Items.COBBLESTONE).count(32, 64).category(LootCategory.BLOCK).weight(40).build())
            .add(LootEntry.builder(Items.STONE_BRICKS).count(32, 64).category(LootCategory.BLOCK).weight(20).build())
            // Food
            .add(LootEntry.builder(Items.COOKED_BEEF).count(6, 10).category(LootCategory.FOOD).weight(40).build())
            .add(LootEntry.builder(Items.BREAD).count(8, 14).category(LootCategory.FOOD).weight(30).build())
            .add(LootEntry.builder(Items.GOLDEN_APPLE).count(1, 2).category(LootCategory.FOOD).weight(15).build())
            // Projectiles
            .add(LootEntry.builder(Items.SNOWBALL).count(16).category(LootCategory.PROJECTILE).weight(25).build())
            .add(LootEntry.builder(Items.EGG).count(16).category(LootCategory.PROJECTILE).weight(20).build())
            .add(LootEntry.builder(Items.BOW).category(LootCategory.PROJECTILE).weight(12).build())
            .add(LootEntry.builder(Items.ARROW).count(12, 24).category(LootCategory.PROJECTILE).weight(20).build())
            .add(LootEntry.builder(Items.FISHING_ROD).category(LootCategory.PROJECTILE).weight(10).build())
            // Utility
            .add(LootEntry.builder(Items.WATER_BUCKET).category(LootCategory.UTILITY).weight(25).build())
            .add(LootEntry.builder(Items.LAVA_BUCKET).category(LootCategory.UTILITY).weight(15).build())
            .add(LootEntry.builder(Items.FLINT_AND_STEEL).category(LootCategory.UTILITY).weight(12).build())
            .add(LootEntry.builder(Items.TNT).count(1, 3).category(LootCategory.UTILITY).weight(12).build())
            .add(LootEntry.builder(Items.ENDER_PEARL).count(1).category(LootCategory.UTILITY).weight(5).build())
            .build();
    }

    public static ChestLootTable createIslandInsane() {
        return ChestLootTable.builder()
            .rolls(5, 7)
            .guarantee(LootCategory.WEAPON)
            .guarantee(LootCategory.BLOCK)
            .guarantee(LootCategory.ARMOR)
            .guarantee(LootCategory.FOOD)
            .maxCategory(LootCategory.WEAPON, 2)
            .maxCategory(LootCategory.ARMOR, 2)
            // Weapons
            .add(LootEntry.builder(Items.IRON_SWORD).enchant(Enchantments.SHARPNESS, 1).category(LootCategory.WEAPON).weight(35).build())
            .add(LootEntry.builder(Items.DIAMOND_SWORD).category(LootCategory.WEAPON).weight(25).build())
            .add(LootEntry.builder(Items.DIAMOND_AXE).category(LootCategory.WEAPON).weight(15).build())
            // Armor
            .add(LootEntry.builder(Items.DIAMOND_HELMET).category(LootCategory.ARMOR).weight(20).build())
            .add(LootEntry.builder(Items.DIAMOND_BOOTS).category(LootCategory.ARMOR).weight(20).build())
            .add(LootEntry.builder(Items.DIAMOND_CHESTPLATE).category(LootCategory.ARMOR).weight(10).build())
            .add(LootEntry.builder(Items.DIAMOND_LEGGINGS).category(LootCategory.ARMOR).weight(12).build())
            .add(LootEntry.builder(Items.IRON_CHESTPLATE).enchant(Enchantments.PROTECTION, 1).category(LootCategory.ARMOR).weight(20).build())
            .add(LootEntry.builder(Items.IRON_LEGGINGS).enchant(Enchantments.PROTECTION, 1).category(LootCategory.ARMOR).weight(20).build())
            // Blocks
            .add(LootEntry.builder(Items.OAK_PLANKS).count(64).category(LootCategory.BLOCK).weight(40).build())
            .add(LootEntry.builder(Items.STONE_BRICKS).count(64).category(LootCategory.BLOCK).weight(40).build())
            // Food
            .add(LootEntry.builder(Items.GOLDEN_APPLE).count(2, 3).category(LootCategory.FOOD).weight(35).build())
            .add(LootEntry.builder(Items.COOKED_BEEF).count(8, 16).category(LootCategory.FOOD).weight(30).build())
            // Projectiles
            .add(LootEntry.builder(Items.BOW).enchant(Enchantments.POWER, 1).category(LootCategory.PROJECTILE).weight(20).build())
            .add(LootEntry.builder(Items.ARROW).count(16, 32).category(LootCategory.PROJECTILE).weight(25).build())
            .add(LootEntry.builder(Items.SNOWBALL).count(16).category(LootCategory.PROJECTILE).weight(20).build())
            // Utility
            .add(LootEntry.builder(Items.ENDER_PEARL).count(1, 2).category(LootCategory.UTILITY).weight(15).build())
            .add(LootEntry.builder(Items.WATER_BUCKET).category(LootCategory.UTILITY).weight(25).build())
            .add(LootEntry.builder(Items.LAVA_BUCKET).category(LootCategory.UTILITY).weight(18).build())
            .add(LootEntry.builder(Items.SPLASH_POTION).potion(Potions.STRONG_SWIFTNESS).category(LootCategory.UTILITY).weight(15).name("Speed II Potion").build())
            .add(LootEntry.builder(Items.SPLASH_POTION).potion(Potions.STRONG_HEALING).category(LootCategory.UTILITY).weight(12).name("Healing II Potion").build())
            .build();
    }

    public static ChestLootTable createMidNormal() {
        return ChestLootTable.builder()
            .rolls(4, 6)
            .guarantee(LootCategory.WEAPON)
            .guarantee(LootCategory.ARMOR)
            .guarantee(LootCategory.UTILITY)
            .maxCategory(LootCategory.WEAPON, 2)
            .maxCategory(LootCategory.ARMOR, 2)
            // Weapons
            .add(LootEntry.builder(Items.DIAMOND_SWORD).enchant(Enchantments.SHARPNESS, 1).category(LootCategory.WEAPON).weight(35).build())
            .add(LootEntry.builder(Items.IRON_SWORD).enchant(Enchantments.KNOCKBACK, 1).category(LootCategory.WEAPON).weight(20).build())
            .add(LootEntry.builder(Items.BOW).enchant(Enchantments.POWER, 2).category(LootCategory.PROJECTILE).weight(25).build())
            .add(LootEntry.builder(Items.ARROW).count(24, 48).category(LootCategory.PROJECTILE).weight(30).build())
            // Armor
            .add(LootEntry.builder(Items.DIAMOND_HELMET).enchant(Enchantments.PROTECTION, 1).category(LootCategory.ARMOR).weight(25).build())
            .add(LootEntry.builder(Items.DIAMOND_CHESTPLATE).category(LootCategory.ARMOR).weight(20).build())
            .add(LootEntry.builder(Items.DIAMOND_LEGGINGS).category(LootCategory.ARMOR).weight(20).build())
            .add(LootEntry.builder(Items.DIAMOND_BOOTS).enchant(Enchantments.PROTECTION, 1).category(LootCategory.ARMOR).weight(25).build())
            // Utility & Food
            .add(LootEntry.builder(Items.ENDER_PEARL).count(2, 4).category(LootCategory.UTILITY).weight(35).build())
            .add(LootEntry.builder(Items.GOLDEN_APPLE).count(2, 4).category(LootCategory.FOOD).weight(30).build())
            .add(LootEntry.builder(Items.SPLASH_POTION).potion(Potions.STRONG_SWIFTNESS).category(LootCategory.UTILITY).weight(20).name("Speed II Potion").build())
            .add(LootEntry.builder(Items.SPLASH_POTION).potion(Potions.REGENERATION).category(LootCategory.UTILITY).weight(15).name("Regen Potion").build())
            .add(LootEntry.builder(Items.WATER_BUCKET).category(LootCategory.UTILITY).weight(20).build())
            .add(LootEntry.builder(Items.TNT).count(4, 8).category(LootCategory.UTILITY).weight(15).build())
            .build();
    }

    public static ChestLootTable createMidInsane() {
        return ChestLootTable.builder()
            .rolls(5, 8)
            .guarantee(LootCategory.WEAPON)
            .guarantee(LootCategory.ARMOR)
            .guarantee(LootCategory.UTILITY)
            .maxCategory(LootCategory.WEAPON, 2)
            .maxCategory(LootCategory.ARMOR, 3)
            // Weapons
            .add(LootEntry.builder(Items.DIAMOND_SWORD).enchant(Enchantments.SHARPNESS, 2).category(LootCategory.WEAPON).weight(35).build())
            .add(LootEntry.builder(Items.DIAMOND_SWORD).enchant(Enchantments.FIRE_ASPECT, 1).category(LootCategory.WEAPON).weight(25).build())
            .add(LootEntry.builder(Items.SLIME_BALL).enchant(Enchantments.KNOCKBACK, 2).category(LootCategory.WEAPON).weight(15).name("Knockback Slimeball").build())
            .add(LootEntry.builder(Items.BOW).enchant(Enchantments.POWER, 3).enchant(Enchantments.PUNCH, 1).category(LootCategory.PROJECTILE).weight(25).build())
            .add(LootEntry.builder(Items.ARROW).count(32, 64).category(LootCategory.PROJECTILE).weight(30).build())
            // Armor
            .add(LootEntry.builder(Items.DIAMOND_HELMET).enchant(Enchantments.PROTECTION, 2).category(LootCategory.ARMOR).weight(25).build())
            .add(LootEntry.builder(Items.DIAMOND_CHESTPLATE).enchant(Enchantments.PROTECTION, 2).category(LootCategory.ARMOR).weight(20).build())
            .add(LootEntry.builder(Items.DIAMOND_LEGGINGS).enchant(Enchantments.PROTECTION, 2).category(LootCategory.ARMOR).weight(20).build())
            .add(LootEntry.builder(Items.DIAMOND_BOOTS).enchant(Enchantments.PROTECTION, 2).category(LootCategory.ARMOR).weight(25).build())
            // Utility & Food
            .add(LootEntry.builder(Items.ENDER_PEARL).count(4, 8).category(LootCategory.UTILITY).weight(40).build())
            .add(LootEntry.builder(Items.GOLDEN_APPLE).count(3, 6).category(LootCategory.FOOD).weight(30).build())
            .add(LootEntry.builder(Items.ENCHANTED_GOLDEN_APPLE).count(1).category(LootCategory.FOOD).weight(8).build())
            .add(LootEntry.builder(Items.SPLASH_POTION).potion(Potions.STRENGTH).category(LootCategory.UTILITY).weight(18).name("Strength Potion").build())
            .add(LootEntry.builder(Items.SPLASH_POTION).potion(Potions.STRONG_SWIFTNESS).category(LootCategory.UTILITY).weight(22).name("Speed II Potion").build())
            .add(LootEntry.builder(Items.SPLASH_POTION).potion(Potions.STRONG_HEALING).category(LootCategory.UTILITY).weight(20).name("Healing II Potion").build())
            .build();
    }

    public static ChestLootTable island(boolean insane) {
        return insane ? createIslandInsane() : createIslandNormal();
    }

    public static ChestLootTable mid(boolean insane) {
        return insane ? createMidInsane() : createMidNormal();
    }
}
