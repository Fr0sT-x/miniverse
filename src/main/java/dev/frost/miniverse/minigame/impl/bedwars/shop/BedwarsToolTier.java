package dev.frost.miniverse.minigame.impl.bedwars.shop;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.Nullable;

public enum BedwarsToolTier {
    // Pickaxe family (tier 0-3)
    WOOD_PICKAXE (0, Items.WOODEN_PICKAXE,  null,               0, "Wooden Pickaxe"),
    STONE_PICKAXE(1, Items.STONE_PICKAXE,   null,               0, "Stone Pickaxe"),
    IRON_PICKAXE (2, Items.IRON_PICKAXE,    RegistryKey.of(RegistryKeys.ENCHANTMENT, net.minecraft.util.Identifier.of("efficiency")), 2, "Iron Pickaxe"),
    GOLD_PICKAXE (3, Items.GOLDEN_PICKAXE,  RegistryKey.of(RegistryKeys.ENCHANTMENT, net.minecraft.util.Identifier.of("efficiency")), 4, "Golden Pickaxe"),

    // Axe family (tier 0-3)
    WOOD_AXE     (0, Items.WOODEN_AXE,      null,               0, "Wooden Axe"),
    STONE_AXE    (1, Items.STONE_AXE,       null,               0, "Stone Axe"),
    IRON_AXE     (2, Items.IRON_AXE,        null,               0, "Iron Axe"),
    DIAMOND_AXE  (3, Items.DIAMOND_AXE,     null,               0, "Diamond Axe");

    private final int tier;
    private final Item item;
    private final RegistryKey<Enchantment> enchantment;
    private final int enchLevel;
    private final String displayName;

    BedwarsToolTier(int tier, Item item, @Nullable RegistryKey<Enchantment> enchantment, int enchLevel, String displayName) {
        this.tier = tier;
        this.item = item;
        this.enchantment = enchantment;
        this.enchLevel = enchLevel;
        this.displayName = displayName;
    }

    public int tier() { return tier; }
    
    public ItemStack buildStack(RegistryWrapper.WrapperLookup reg) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal(displayName));
        if (enchantment != null) {
            reg.getOptionalWrapper(RegistryKeys.ENCHANTMENT).flatMap(r -> r.getOptional(enchantment))
                .ifPresent(e -> stack.addEnchantment(e, enchLevel));
        }
        return stack;
    }

    public static BedwarsToolTier pickaxeAtTier(int tier) {
        int clamp = Math.max(0, Math.min(3, tier));
        return switch (clamp) {
            case 0 -> WOOD_PICKAXE;
            case 1 -> STONE_PICKAXE;
            case 2 -> IRON_PICKAXE;
            case 3 -> GOLD_PICKAXE;
            default -> WOOD_PICKAXE;
        };
    }

    public static BedwarsToolTier axeAtTier(int tier) {
        int clamp = Math.max(0, Math.min(3, tier));
        return switch (clamp) {
            case 0 -> WOOD_AXE;
            case 1 -> STONE_AXE;
            case 2 -> IRON_AXE;
            case 3 -> DIAMOND_AXE;
            default -> WOOD_AXE;
        };
    }
}
