package dev.frost.miniverse.minigame.core.loot;

/**
 * High-level categories for minigame chest loot items.
 * Allows loot tables to guarantee category diversity (e.g. at least 1 weapon, at least 1 block stack).
 */
public enum LootCategory {
    WEAPON,
    ARMOR,
    BLOCK,
    FOOD,
    PROJECTILE,
    UTILITY,
    MISC
}
