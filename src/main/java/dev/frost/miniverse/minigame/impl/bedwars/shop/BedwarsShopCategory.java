package dev.frost.miniverse.minigame.impl.bedwars.shop;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

public enum BedwarsShopCategory {
    QUICK_BUY (Items.NETHER_STAR, "Quick Buy"),
    BLOCKS    (Items.TERRACOTTA, "Blocks"),
    MELEE     (Items.GOLDEN_SWORD, "Melee"),
    ARMOR     (Items.CHAINMAIL_BOOTS, "Armor"),
    TOOLS     (Items.STONE_PICKAXE, "Tools"),
    RANGED    (Items.BOW, "Ranged"),
    POTIONS   (Items.POTION, "Potions"),
    UTILITY   (Items.TNT, "Utility");

    private final Item icon;
    private final String displayName;

    BedwarsShopCategory(Item icon, String displayName) {
        this.icon = icon;
        this.displayName = displayName;
    }

    public Item icon() { return icon; }
    public String displayName() { return displayName; }
}
