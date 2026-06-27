package dev.frost.miniverse.minigame.impl.bedwars.economy;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;

public enum BedwarsCurrency {
    IRON   (Items.IRON_INGOT,  "Iron",    Formatting.WHITE),
    GOLD   (Items.GOLD_INGOT,  "Gold",    Formatting.GOLD),
    DIAMOND(Items.DIAMOND,     "Diamond", Formatting.AQUA),
    EMERALD(Items.EMERALD,     "Emerald", Formatting.GREEN);

    private final Item item;
    private final String displayName;
    private final Formatting formatting;

    BedwarsCurrency(Item item, String displayName, Formatting formatting) {
        this.item = item;
        this.displayName = displayName;
        this.formatting = formatting;
    }

    public Item item() { return this.item; }
    public String displayName() { return this.displayName; }
    public Formatting formatting() { return this.formatting; }
}
