package dev.frost.miniverse.minigame.core.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class ProtectedItemTags {
    public static final String TAG_PROTECTED = "protected_item";
    public static final String TAG_PROTECTED_TYPE = "protected_item_type";
    public static final String TAG_ALLOW_REARRANGE = "protected_item_allow_rearrange";
    public static final String TAG_ALLOW_DROP = "protected_item_allow_drop";
    public static final String TAG_ALLOW_OFFHAND = "protected_item_allow_offhand";

    private ProtectedItemTags() {
    }

    public static void mark(ItemStack stack, String type) {
        String normalized = normalizeType(type);
        if (normalized == null) {
            return;
        }
        boolean allowRearrange = ProtectedItemTypes.TRACKER_COMPASS.equals(normalized);
        boolean allowDrop = false;
        boolean allowOffhand = ProtectedItemTypes.TRACKER_COMPASS.equals(normalized);
        mark(stack, normalized, allowRearrange, allowDrop, allowOffhand);
    }

    public static void mark(ItemStack stack, String type, boolean allowRearrange, boolean allowDrop, boolean allowOffhandSwap) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String normalized = normalizeType(type);
        if (normalized == null) {
            return;
        }
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            nbt.putBoolean(TAG_PROTECTED, true);
            nbt.putString(TAG_PROTECTED_TYPE, normalized);
            nbt.putBoolean(TAG_ALLOW_REARRANGE, allowRearrange);
            nbt.putBoolean(TAG_ALLOW_DROP, allowDrop);
            nbt.putBoolean(TAG_ALLOW_OFFHAND, allowOffhandSwap);
        });
    }

    public static boolean isProtected(ItemStack stack) {
        return getType(stack) != null;
    }

    public static boolean hasType(ItemStack stack, String type) {
        String normalized = normalizeType(type);
        if (normalized == null) {
            return false;
        }
        String current = getType(stack);
        return normalized.equals(current);
    }

    public static boolean canRearrange(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return true;
        }
        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(TAG_PROTECTED, NbtElement.NUMBER_TYPE) || !nbt.getBoolean(TAG_PROTECTED)) {
            return true;
        }
        if (nbt.contains(TAG_ALLOW_REARRANGE, NbtElement.NUMBER_TYPE)) {
            return nbt.getBoolean(TAG_ALLOW_REARRANGE);
        }
        String type = getType(stack);
        return ProtectedItemTypes.TRACKER_COMPASS.equals(type);
    }

    public static boolean canDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return true;
        }
        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(TAG_PROTECTED, NbtElement.NUMBER_TYPE) || !nbt.getBoolean(TAG_PROTECTED)) {
            return true;
        }
        if (nbt.contains(TAG_ALLOW_DROP, NbtElement.NUMBER_TYPE)) {
            return nbt.getBoolean(TAG_ALLOW_DROP);
        }
        return false;
    }

    public static boolean canOffhandSwap(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return true;
        }
        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(TAG_PROTECTED, NbtElement.NUMBER_TYPE) || !nbt.getBoolean(TAG_PROTECTED)) {
            return true;
        }
        if (nbt.contains(TAG_ALLOW_OFFHAND, NbtElement.NUMBER_TYPE)) {
            return nbt.getBoolean(TAG_ALLOW_OFFHAND);
        }
        String type = getType(stack);
        return ProtectedItemTypes.TRACKER_COMPASS.equals(type);
    }

    @Nullable
    public static String getType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(TAG_PROTECTED, NbtElement.NUMBER_TYPE) || !nbt.getBoolean(TAG_PROTECTED)) {
            return null;
        }
        String type = nbt.contains(TAG_PROTECTED_TYPE, NbtElement.STRING_TYPE) ? nbt.getString(TAG_PROTECTED_TYPE) : "";
        if (type.isBlank()) {
            return null;
        }
        return type;
    }

    static @Nullable String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}


