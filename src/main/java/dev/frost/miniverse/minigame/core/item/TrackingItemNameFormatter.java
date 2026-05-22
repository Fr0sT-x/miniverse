package dev.frost.miniverse.minigame.core.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Predicate;

public final class TrackingItemNameFormatter {
    private TrackingItemNameFormatter() {
    }

    public static Text buildTrackingName(Text targetName) {
        return Text.literal("Tracking: ")
            .formatted(Formatting.GRAY)
            .append(targetName.copy());
    }

    public static void applyTrackingName(PlayerInventory inventory, Predicate<ItemStack> isTrackingItem, Text targetName) {
        if (inventory == null || targetName == null) {
            return;
        }

        Text displayName = buildTrackingName(targetName);
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!isTrackingItem.test(stack)) {
                continue;
            }
            stack.set(DataComponentTypes.CUSTOM_NAME, displayName);
        }
    }
}

