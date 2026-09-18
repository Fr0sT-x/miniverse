package dev.frost.miniverse.minigame.impl.zombies.item;

import dev.frost.miniverse.minigame.core.item.ProtectedItemTags;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTypes;
import dev.frost.miniverse.minigame.impl.zombies.perk.PlayerPerk;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponItemHelper;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class ZombiesHotbarManager {
    public static final int SLOT_KNIFE = 0;
    public static final int SLOT_WEAPON_1 = 1;
    public static final int SLOT_WEAPON_2 = 2;
    public static final int SLOT_WEAPON_3 = 3;
    public static final int SLOT_PERK_1 = 6;
    public static final int SLOT_PERK_2 = 7;
    public static final int SLOT_PERK_3 = 8;

    private static final String KEY_PLACEHOLDER_KIND = "zombies_placeholder_kind";
    private static final String KIND_EMPTY_WEAPON = "empty_weapon";
    private static final String KIND_LOCKED_WEAPON = "locked_weapon";
    private static final String KIND_EMPTY_PERK = "empty_perk";

    private ZombiesHotbarManager() {}

    public static ItemStack createEmptyWeaponPlaceholder() {
        ItemStack stack = new ItemStack(Items.GUNPOWDER);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Empty Weapon Slot").formatted(Formatting.GRAY));
        stack.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Purchase or roll a weapon to fill").formatted(Formatting.DARK_GRAY)
        )));
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putString(KEY_PLACEHOLDER_KIND, KIND_EMPTY_WEAPON));
        ProtectedItemTags.mark(stack, ProtectedItemTypes.ZOMBIES_PLACEHOLDER);
        return stack;
    }

    public static ItemStack createLockedWeaponPlaceholder() {
        ItemStack stack = new ItemStack(Items.GUNPOWDER);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Locked Weapon Slot").formatted(Formatting.DARK_GRAY));
        stack.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Requires Extra Weapon Perk").formatted(Formatting.RED)
        )));
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putString(KEY_PLACEHOLDER_KIND, KIND_LOCKED_WEAPON));
        ProtectedItemTags.mark(stack, ProtectedItemTypes.ZOMBIES_PLACEHOLDER);
        return stack;
    }

    public static ItemStack createEmptyPerkPlaceholder() {
        ItemStack stack = new ItemStack(Items.GUNPOWDER);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Empty Perk Slot").formatted(Formatting.GRAY));
        stack.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Purchase a perk from a machine to fill").formatted(Formatting.DARK_GRAY)
        )));
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putString(KEY_PLACEHOLDER_KIND, KIND_EMPTY_PERK));
        ProtectedItemTags.mark(stack, ProtectedItemTypes.ZOMBIES_PLACEHOLDER);
        return stack;
    }

    public static void setupInitialHotbar(ServerPlayerEntity player) {
        setupInitialHotbar(player, null);
    }

    public static void setupInitialHotbar(ServerPlayerEntity player, dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponCustomConfig config) {
        PlayerInventory inv = player.getInventory();
        inv.clear();

        inv.setStack(SLOT_KNIFE, WeaponItemHelper.createWeaponStack(WeaponType.KNIFE, config));
        inv.setStack(SLOT_WEAPON_1, WeaponItemHelper.createWeaponStack(WeaponType.PISTOL, config));
        inv.setStack(SLOT_WEAPON_2, createEmptyWeaponPlaceholder());
        inv.setStack(SLOT_WEAPON_3, createLockedWeaponPlaceholder());
        inv.setStack(4, ItemStack.EMPTY);
        inv.setStack(5, ItemStack.EMPTY);
        inv.setStack(SLOT_PERK_1, createEmptyPerkPlaceholder());
        inv.setStack(SLOT_PERK_2, createEmptyPerkPlaceholder());
        inv.setStack(SLOT_PERK_3, createEmptyPerkPlaceholder());

        player.playerScreenHandler.sendContentUpdates();
    }

    public static boolean isPerkSlot(int slotIndex) {
        return slotIndex >= SLOT_PERK_1 && slotIndex <= SLOT_PERK_3;
    }

    public static boolean isWeaponSlot(int slotIndex, boolean hasExtraWeapon) {
        if (slotIndex == SLOT_WEAPON_1 || slotIndex == SLOT_WEAPON_2) return true;
        return hasExtraWeapon && slotIndex == SLOT_WEAPON_3;
    }

    public static boolean isKnifeSlot(int slotIndex) {
        return slotIndex == SLOT_KNIFE;
    }

    public static boolean isPerkItem(ItemStack stack) {
        return ProtectedItemTags.hasType(stack, ProtectedItemTypes.ZOMBIES_PERK);
    }

    public static boolean isPlaceholder(ItemStack stack) {
        return ProtectedItemTags.hasType(stack, ProtectedItemTypes.ZOMBIES_PLACEHOLDER);
    }

    public static boolean isEmptyWeaponSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (!isPlaceholder(stack)) return false;
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null) return false;
        return KIND_EMPTY_WEAPON.equals(comp.copyNbt().getString(KEY_PLACEHOLDER_KIND));
    }

    public static boolean isLockedWeaponSlot(ItemStack stack) {
        if (!isPlaceholder(stack)) return false;
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null) return false;
        return KIND_LOCKED_WEAPON.equals(comp.copyNbt().getString(KEY_PLACEHOLDER_KIND));
    }

    public static boolean isEmptyPerkSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (!isPlaceholder(stack)) return false;
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null) return false;
        return KIND_EMPTY_PERK.equals(comp.copyNbt().getString(KEY_PLACEHOLDER_KIND));
    }

    public static int getFirstOpenWeaponSlot(ServerPlayerEntity player, boolean hasExtraWeapon) {
        PlayerInventory inv = player.getInventory();
        if (isEmptyWeaponSlot(inv.getStack(SLOT_WEAPON_1))) return SLOT_WEAPON_1;
        if (isEmptyWeaponSlot(inv.getStack(SLOT_WEAPON_2))) return SLOT_WEAPON_2;
        if (hasExtraWeapon && isEmptyWeaponSlot(inv.getStack(SLOT_WEAPON_3))) return SLOT_WEAPON_3;
        return -1;
    }

    public static int getFirstOpenPerkSlot(ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        if (isEmptyPerkSlot(inv.getStack(SLOT_PERK_1))) return SLOT_PERK_1;
        if (isEmptyPerkSlot(inv.getStack(SLOT_PERK_2))) return SLOT_PERK_2;
        if (isEmptyPerkSlot(inv.getStack(SLOT_PERK_3))) return SLOT_PERK_3;
        return -1;
    }

    public static void unlockExtraWeaponSlot(ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        ItemStack current = inv.getStack(SLOT_WEAPON_3);
        if (isLockedWeaponSlot(current) || current.isEmpty()) {
            inv.setStack(SLOT_WEAPON_3, createEmptyWeaponPlaceholder());
            player.playerScreenHandler.sendContentUpdates();
        }
    }

    public static void lockExtraWeaponSlot(ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        inv.setStack(SLOT_WEAPON_3, createLockedWeaponPlaceholder());
        player.playerScreenHandler.sendContentUpdates();
    }

    public static void resetPerkSlots(ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        inv.setStack(SLOT_PERK_1, createEmptyPerkPlaceholder());
        inv.setStack(SLOT_PERK_2, createEmptyPerkPlaceholder());
        inv.setStack(SLOT_PERK_3, createEmptyPerkPlaceholder());
        player.playerScreenHandler.sendContentUpdates();
    }

    public static PlayerPerk getPerkAtSlot(ServerPlayerEntity player, int slotIndex) {
        if (!isPerkSlot(slotIndex)) return null;
        ItemStack stack = player.getInventory().getStack(slotIndex);
        if (!isPerkItem(stack)) return null;
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp != null && comp.contains("zombies_perk_id")) {
            return PlayerPerk.fromString(comp.copyNbt().getString("zombies_perk_id"));
        }
        for (PlayerPerk perk : PlayerPerk.values()) {
            if (perk.getIcon() == stack.getItem()) {
                return perk;
            }
        }
        return null;
    }
}
