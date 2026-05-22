package dev.frost.miniverse.minigame.core.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class ProtectedItemTags {
    public static final String TAG_PROTECTED = "protected_item";
    public static final String TAG_PROTECTED_TYPE = "protected_item_type";

    private ProtectedItemTags() {
    }

    public static void mark(ItemStack stack, String type) {
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
        if (nbt.getBoolean(TAG_PROTECTED).orElse(false) == false) {
            return null;
        }
        String type = nbt.getString(TAG_PROTECTED_TYPE).orElse("");
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


