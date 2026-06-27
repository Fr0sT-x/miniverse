package dev.frost.miniverse.minigame.impl.bedwars.shop;

import dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.potion.Potions;
import org.jetbrains.annotations.Nullable;

import static dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.*;
import static dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsShopCategory.*;

public enum BedwarsShopItem {
    // --- BLOCKS ---
    WOOL              (Items.WHITE_WOOL,           "Wool",                       BLOCKS,  IRON,    4, 16),
    HARDENED_CLAY     (Items.TERRACOTTA,            "Hardened Clay",              BLOCKS,  IRON,   12, 16),
    BLAST_PROOF_GLASS (Items.WHITE_STAINED_GLASS,  "Blast-Proof Glass",          BLOCKS,  IRON,   12,  4),
    END_STONE         (Items.END_STONE,             "End Stone",                  BLOCKS,  IRON,   24, 12),
    LADDER            (Items.LADDER,                "Ladder",                     BLOCKS,  IRON,    4, 16),
    OAK_PLANKS        (Items.OAK_PLANKS,            "Oak Wood Planks",            BLOCKS,  IRON,    4, 16),
    OBSIDIAN          (Items.OBSIDIAN,              "Obsidian",                   BLOCKS,  EMERALD, 4,  4),

    // --- MELEE ---
    STONE_SWORD       (Items.STONE_SWORD,           "Stone Sword",                MELEE,   IRON,   10,  1),
    IRON_SWORD        (Items.IRON_SWORD,            "Iron Sword",                 MELEE,   GOLD,    7,  1),
    DIAMOND_SWORD     (Items.DIAMOND_SWORD,         "Diamond Sword",              MELEE,   EMERALD, 4,  1),
    KNOCKBACK_STICK   (Items.STICK,                 "Stick",                      MELEE,   GOLD,    5,  1),

    // --- ARMOR ---
    CHAINMAIL_ARMOR   (null,                        "Chainmail Armor",            ARMOR,   IRON,   40,  0),
    IRON_ARMOR        (null,                        "Iron Armor",                 ARMOR,   GOLD,   12,  0),
    DIAMOND_ARMOR     (null,                        "Diamond Armor",              ARMOR,   EMERALD, 6,  0),

    // --- TOOLS ---
    PICKAXE_STONE     (Items.STONE_PICKAXE,         "Stone Pickaxe",              TOOLS,   IRON,   10,  1),
    PICKAXE_IRON      (Items.IRON_PICKAXE,          "Iron Pickaxe",               TOOLS,   IRON,   20,  1),
    PICKAXE_GOLD      (Items.GOLDEN_PICKAXE,        "Golden Pickaxe",             TOOLS,   IRON,   30,  1),
    AXE_STONE         (Items.STONE_AXE,             "Stone Axe",                  TOOLS,   IRON,   10,  1),
    AXE_IRON          (Items.IRON_AXE,              "Iron Axe",                   TOOLS,   IRON,   20,  1),
    AXE_DIAMOND       (Items.DIAMOND_AXE,           "Diamond Axe",                TOOLS,   GOLD,    3,  1),
    SHEARS            (Items.SHEARS,                "Shears",                     TOOLS,   IRON,   20,  1),

    // --- RANGED ---
    ARROW             (Items.ARROW,                 "Arrow",                      RANGED,  GOLD,    2,  6),
    BOW               (Items.BOW,                   "Bow",                        RANGED,  GOLD,   12,  1),
    BOW_POWER         (Items.BOW,                   "Bow (Power I)",              RANGED,  GOLD,   24,  1),
    BOW_PUNCH         (Items.BOW,                   "Bow (Power I, Punch I)",     RANGED,  GOLD,   36,  1),

    // --- POTIONS ---
    SPEED_POTION      (Items.POTION,                "Speed II (45s)",             POTIONS, GOLD,    2,  1),
    JUMP_POTION       (Items.POTION,                "Jump V (45s)",               POTIONS, GOLD,    2,  1),
    INVIS_POTION      (Items.POTION,                "Invisibility (30s)",         POTIONS, GOLD,    4,  1),

    // --- UTILITY ---
    GOLDEN_APPLE      (Items.GOLDEN_APPLE,          "Golden Apple",               UTILITY, GOLD,    3,  1),
    FIREBALL          (Items.FIRE_CHARGE,           "Fireball",                   UTILITY, IRON,   40,  1),
    TNT               (Items.TNT,                   "TNT",                        UTILITY, GOLD,    4,  1),
    ENDER_PEARL       (Items.ENDER_PEARL,           "Ender Pearl",                UTILITY, GOLD,    4,  1),
    WATER_BUCKET      (Items.WATER_BUCKET,          "Water Bucket",               UTILITY, GOLD,    4,  1),
    SPONGE            (Items.SPONGE,                "Sponge",                     UTILITY, GOLD,    3,  1);

    private final Item item;
    private final String displayName;
    private final BedwarsShopCategory category;
    private final BedwarsCurrency currency;
    private final int cost;
    private final int stackSize;

    BedwarsShopItem(@Nullable Item item, String displayName, BedwarsShopCategory category, BedwarsCurrency currency, int cost, int stackSize) {
        this.item = item;
        this.displayName = displayName;
        this.category = category;
        this.currency = currency;
        this.cost = cost;
        this.stackSize = stackSize;
    }

    @Nullable public Item item() { return this.item; }
    public String displayName() { return this.displayName; }
    public BedwarsShopCategory category() { return this.category; }
    public BedwarsCurrency currency() { return this.currency; }
    public int cost() { return this.cost; }
    public int stackSize() { return this.stackSize; }

    public ItemStack buildStack(RegistryWrapper.WrapperLookup reg) {
        if (this.item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(this.item, Math.max(1, this.stackSize));
        stack.set(DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal(this.displayName));

        if (this == KNOCKBACK_STICK) {
            reg.getOptionalWrapper(RegistryKeys.ENCHANTMENT).flatMap(r -> r.getOptional(Enchantments.KNOCKBACK))
                .ifPresent(e -> stack.addEnchantment(e, 1));
        } else if (this == BOW_POWER) {
            reg.getOptionalWrapper(RegistryKeys.ENCHANTMENT).flatMap(r -> r.getOptional(Enchantments.POWER))
                .ifPresent(e -> stack.addEnchantment(e, 1));
        } else if (this == BOW_PUNCH) {
            reg.getOptionalWrapper(RegistryKeys.ENCHANTMENT).flatMap(r -> r.getOptional(Enchantments.POWER))
                .ifPresent(e -> stack.addEnchantment(e, 1));
            reg.getOptionalWrapper(RegistryKeys.ENCHANTMENT).flatMap(r -> r.getOptional(Enchantments.PUNCH))
                .ifPresent(e -> stack.addEnchantment(e, 1));
        } else if (this == SPEED_POTION) {
            stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.SWIFTNESS));
        } else if (this == JUMP_POTION) {
            stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.LEAPING));
        } else if (this == INVIS_POTION) {
            stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.INVISIBILITY));
        }

        return stack;
    }
}
