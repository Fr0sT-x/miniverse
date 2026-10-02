package dev.frost.miniverse.minigame.impl.ctf.powerup;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum CtfPowerupType {
    SPEED("Speed Boost", Items.SUGAR, Formatting.AQUA),
    STRENGTH("Strength", Items.BLAZE_POWDER, Formatting.RED),
    ABSORPTION("Absorption Shield", Items.GOLDEN_APPLE, Formatting.GOLD),
    HEAL("Healing", Items.GLISTERING_MELON_SLICE, Formatting.LIGHT_PURPLE),
    JUMP_BOOST("Jump Boost", Items.RABBIT_FOOT, Formatting.GREEN),
    COINS("Coin Stash (+30)", Items.SUNFLOWER, Formatting.YELLOW),
    GEMS("Gem Cache (+10)", Items.EMERALD, Formatting.DARK_GREEN),
    BRIDGE_BALL("Bridge Egg", Items.EGG, Formatting.WHITE);

    private final String displayName;
    private final Item icon;
    private final Formatting formatting;

    CtfPowerupType(String displayName, Item icon, Formatting formatting) {
        this.displayName = displayName;
        this.icon = icon;
        this.formatting = formatting;
    }

    public String displayName() { return displayName; }
    public Item icon() { return icon; }
    public Formatting formatting() { return formatting; }

    public Text getTitleText() {
        return Text.literal("✦ " + displayName + " Powerup").formatted(formatting, Formatting.BOLD);
    }

    public ItemStack createIconStack() {
        ItemStack stack = new ItemStack(icon);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, getTitleText());
        return stack;
    }
}
