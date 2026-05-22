package dev.frost.miniverse.minigame.impl.manhunt;

import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

public final class ManhuntTrackerProtection {
    private ManhuntTrackerProtection() {
    }

    public static void register() {
        // Mixins handle the actual blocking. This method exists for consistency.
    }

    public static boolean isHunter(ServerPlayerEntity player) {
        ManhuntMinigame manhunt = activeManhunt();
        return manhunt != null && manhunt.getPlayerRole(player) == ManhuntMinigame.ManhuntRole.HUNTER;
    }

    public static boolean isTracker(ItemStack stack) {
        return ManhuntMinigame.isTrackerCompass(stack);
    }

    public static boolean hasExternalInventory(ScreenHandler handler, PlayerInventory playerInventory) {
        for (Slot slot : handler.slots) {
            if (slot.inventory != playerInventory) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack getSwapStack(ServerPlayerEntity player, int slotIndex) {
        PlayerInventory inventory = player.getInventory();
        if (slotIndex >= 0 && slotIndex < inventory.size()) {
            return inventory.getStack(slotIndex);
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    public static ManhuntMinigame activeManhunt() {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof ManhuntMinigame manhunt) {
            return manhunt;
        }
        return null;
    }
}



