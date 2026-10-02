package dev.frost.miniverse.minigame.impl.ctf.economy;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public class CtfPlayerUpgradeState {
    private int armorTier = 0;       // 0: Leather, 1: Chain, 2: Iron, 3: Diamond, 4: Netherite
    private int swordTier = 0;       // 0: Wood, 1: Stone, 2: Iron, 3: Diamond, 4: Netherite
    private int protectionTier = 0;  // 0, 1: Prot I, 2: Prot II, 3: Prot III
    private int sharpnessTier = 0;   // 0, 1: Sharp I, 2: Sharp II
    private int powerTier = 0;       // 0, 1: Power I
    private boolean hasShears = false;
    private boolean hasAxe = false;

    public int getArmorTier() { return armorTier; }
    public void upgradeArmor() { if (armorTier < 4) armorTier++; }

    public int getSwordTier() { return swordTier; }
    public void upgradeSword() { if (swordTier < 4) swordTier++; }

    public int getProtectionTier() { return protectionTier; }
    public void upgradeProtection() { if (protectionTier < 3) protectionTier++; }

    public int getSharpnessTier() { return sharpnessTier; }
    public void upgradeSharpness() { if (sharpnessTier < 2) sharpnessTier++; }

    public int getPowerTier() { return powerTier; }
    public void upgradePower() { if (powerTier < 1) powerTier++; }

    public boolean hasShears() { return hasShears; }
    public void setHasShears(boolean val) { this.hasShears = val; }

    public boolean hasAxe() { return hasAxe; }
    public void setHasAxe(boolean val) { this.hasAxe = val; }

    public ItemStack buildSword(ServerPlayerEntity player) {
        Item item = switch (swordTier) {
            case 1 -> Items.STONE_SWORD;
            case 2 -> Items.IRON_SWORD;
            case 3 -> Items.DIAMOND_SWORD;
            case 4 -> Items.NETHERITE_SWORD;
            default -> Items.WOODEN_SWORD;
        };
        ItemStack stack = new ItemStack(item);
        if (sharpnessTier > 0) {
            var reg = player.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            reg.getEntry(Enchantments.SHARPNESS).ifPresent(e -> stack.addEnchantment(e, sharpnessTier));
        }
        return stack;
    }

    public ItemStack buildBow(ServerPlayerEntity player) {
        ItemStack bow = new ItemStack(Items.BOW);
        if (powerTier > 0) {
            var reg = player.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            reg.getEntry(Enchantments.POWER).ifPresent(e -> bow.addEnchantment(e, powerTier));
        }
        return bow;
    }

    public void equipArmor(ServerPlayerEntity player, Formatting teamColor) {
        Integer rgb = teamColor != null ? teamColor.getColorValue() : null;
        int colorInt = rgb != null ? rgb : 0xFFFFFF;

        Item helmet = switch (armorTier) {
            case 1 -> Items.CHAINMAIL_HELMET;
            case 2 -> Items.IRON_HELMET;
            case 3 -> Items.DIAMOND_HELMET;
            case 4 -> Items.NETHERITE_HELMET;
            default -> Items.LEATHER_HELMET;
        };

        Item chestplate = switch (armorTier) {
            case 1 -> Items.CHAINMAIL_CHESTPLATE;
            case 2 -> Items.IRON_CHESTPLATE;
            case 3 -> Items.DIAMOND_CHESTPLATE;
            case 4 -> Items.NETHERITE_CHESTPLATE;
            default -> Items.LEATHER_CHESTPLATE;
        };

        Item leggings = switch (armorTier) {
            case 1 -> Items.CHAINMAIL_LEGGINGS;
            case 2 -> Items.IRON_LEGGINGS;
            case 3 -> Items.DIAMOND_LEGGINGS;
            case 4 -> Items.NETHERITE_LEGGINGS;
            default -> Items.LEATHER_LEGGINGS;
        };

        Item boots = switch (armorTier) {
            case 1 -> Items.CHAINMAIL_BOOTS;
            case 2 -> Items.IRON_BOOTS;
            case 3 -> Items.DIAMOND_BOOTS;
            case 4 -> Items.NETHERITE_BOOTS;
            default -> Items.LEATHER_BOOTS;
        };

        ItemStack hStack = new ItemStack(helmet);
        ItemStack cStack = new ItemStack(chestplate);
        ItemStack lStack = new ItemStack(leggings);
        ItemStack bStack = new ItemStack(boots);

        // Dye leather pieces
        if (armorTier == 0) {
            hStack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(colorInt, false));
            cStack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(colorInt, false));
            lStack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(colorInt, false));
            bStack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(colorInt, false));
        }

        var reg = player.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        reg.getEntry(Enchantments.DEPTH_STRIDER).ifPresent(e -> {
            bStack.addEnchantment(e, 2);
        });

        if (protectionTier > 0) {
            reg.getEntry(Enchantments.PROTECTION).ifPresent(e -> {
                hStack.addEnchantment(e, protectionTier);
                cStack.addEnchantment(e, protectionTier);
                lStack.addEnchantment(e, protectionTier);
                bStack.addEnchantment(e, protectionTier);
            });
        }

        // Only equip helmet if not currently carrying a flag!
        if (player.getEquippedStack(EquipmentSlot.HEAD).getItem() != Items.RED_BANNER &&
            player.getEquippedStack(EquipmentSlot.HEAD).getItem() != Items.BLUE_BANNER &&
            player.getEquippedStack(EquipmentSlot.HEAD).getItem() != Items.GREEN_BANNER &&
            player.getEquippedStack(EquipmentSlot.HEAD).getItem() != Items.YELLOW_BANNER &&
            player.getEquippedStack(EquipmentSlot.HEAD).getItem() != Items.CYAN_BANNER &&
            player.getEquippedStack(EquipmentSlot.HEAD).getItem() != Items.MAGENTA_BANNER) {
            player.equipStack(EquipmentSlot.HEAD, hStack);
        }
        player.equipStack(EquipmentSlot.CHEST, cStack);
        player.equipStack(EquipmentSlot.LEGS, lStack);
        player.equipStack(EquipmentSlot.FEET, bStack);
    }
}
