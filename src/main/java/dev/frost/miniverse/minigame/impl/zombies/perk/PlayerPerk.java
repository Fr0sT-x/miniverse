package dev.frost.miniverse.minigame.impl.zombies.perk;

import dev.frost.miniverse.minigame.core.item.ProtectedItemTags;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTypes;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public enum PlayerPerk {
    SPEED("Speed", Items.RABBIT_FOOT, Formatting.YELLOW, 500),
    EXTRA_HEALTH("Extra Health", Items.GOLDEN_APPLE, Formatting.RED, 750),
    QUICK_FIRE("Quick Fire", Items.WOODEN_HOE, Formatting.LIGHT_PURPLE, 750),
    FAST_REVIVE("Fast Revive", Items.COOKIE, Formatting.BLUE, 500),
    EXTRA_WEAPON("Extra Weapon", Items.CHEST, Formatting.GREEN, 750),
    FROZEN_BULLETS("Frozen Bullets", Items.ICE, Formatting.AQUA, 500),
    FLAME_BULLETS("Flame Bullets", Items.FIRE_CHARGE, Formatting.GOLD, 750);

    private final String displayName;
    private final Item icon;
    private final Formatting color;
    private final int defaultGold;

    PlayerPerk(String displayName, Item icon, Formatting color, int defaultGold) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.defaultGold = defaultGold;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public Item getIcon() {
        return this.icon;
    }

    public Formatting getColor() {
        return this.color;
    }

    public int getDefaultGold() {
        return this.defaultGold;
    }

    public Text toFormattedText() {
        return Text.literal(this.displayName).formatted(this.color, Formatting.BOLD);
    }

    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(this.icon);
        stack.set(DataComponentTypes.CUSTOM_NAME, toFormattedText());
        stack.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        List<Text> lore = List.of(
            Text.literal("Perk").formatted(Formatting.GRAY),
            Text.literal("Effect: " + this.displayName).formatted(Formatting.DARK_GRAY)
        );
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putString("zombies_perk_id", this.name()));
        ProtectedItemTags.mark(stack, ProtectedItemTypes.ZOMBIES_PERK);
        return stack;
    }

    public static PlayerPerk fromString(String name) {
        if (name == null) return SPEED;
        try {
            return PlayerPerk.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SPEED;
        }
    }
}
