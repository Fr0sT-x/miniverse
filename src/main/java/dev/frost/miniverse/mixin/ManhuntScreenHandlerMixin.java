package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.impl.manhunt.ManhuntTrackerProtection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ManhuntScreenHandlerMixin {
    @Shadow public DefaultedList<Slot> slots;
    @Shadow public abstract ItemStack getCursorStack();

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void miniverse$blockTrackerMoves(int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (!ManhuntTrackerProtection.isHunter(serverPlayer)) {
            return;
        }

        PlayerInventory inventory = serverPlayer.getInventory();
        if (!ManhuntTrackerProtection.hasExternalInventory((ScreenHandler) (Object) this, inventory)) {
            return;
        }

        ItemStack cursorStack = this.getCursorStack();
        boolean cursorIsTracker = ManhuntTrackerProtection.isTracker(cursorStack);

        if (slotId < 0 || slotId >= this.slots.size()) {
            if (cursorIsTracker) {
                ci.cancel();
            }
            return;
        }

        Slot slot = this.slots.get(slotId);
        ItemStack slotStack = slot.getStack();
        boolean slotIsTracker = ManhuntTrackerProtection.isTracker(slotStack);
        boolean slotIsPlayer = slot.inventory == inventory;

        if (actionType == SlotActionType.THROW) {
            if (slotIsTracker || cursorIsTracker) {
                ci.cancel();
            }
            return;
        }

        if (actionType == SlotActionType.QUICK_MOVE) {
            if (slotIsTracker) {
                ci.cancel();
            }
            return;
        }

        if (actionType == SlotActionType.SWAP) {
            if (!slotIsPlayer && ManhuntTrackerProtection.isTracker(ManhuntTrackerProtection.getSwapStack(serverPlayer, button))) {
                ci.cancel();
            }
            return;
        }

        if (actionType == SlotActionType.CLONE && slotIsTracker) {
            ci.cancel();
            return;
        }

        if ((slotIsTracker || cursorIsTracker) && !slotIsPlayer) {
            ci.cancel();
            return;
        }

        if (actionType == SlotActionType.QUICK_CRAFT && cursorIsTracker && !slotIsPlayer) {
            ci.cancel();
        }
    }
}

