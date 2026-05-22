package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.core.item.ProtectedItemFeedback;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class ProtectedItemClientHandledScreenMixin<T extends ScreenHandler> {
    @Shadow protected T handler;

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void miniverse$blockProtectedItemMoves(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        ItemStack cursorStack = this.handler.getCursorStack();
        boolean cursorProtected = ProtectedItemTags.isProtected(cursorStack);
        ItemStack slotStack = slot == null ? ItemStack.EMPTY : slot.getStack();
        boolean slotProtected = ProtectedItemTags.isProtected(slotStack);
        if (!cursorProtected && !slotProtected) {
            return;
        }

        boolean slotValid = slotId >= 0 && slotId < this.handler.slots.size();
        boolean slotIsPlayer = slotValid && slot != null && slot.inventory == client.player.getInventory();

        if (!slotValid) {
            if (actionType == SlotActionType.PICKUP || actionType == SlotActionType.THROW) {
                ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                ci.cancel();
            }
            return;
        }

        if (actionType == SlotActionType.THROW || actionType == SlotActionType.CLONE) {
            ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
            ci.cancel();
            return;
        }

        if (actionType == SlotActionType.QUICK_MOVE) {
            if (!slotIsPlayer) {
                ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                ci.cancel();
            }
            return;
        }

        if (actionType == SlotActionType.SWAP) {
            if (!slotIsPlayer) {
                ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                ci.cancel();
            }
            return;
        }

        if (actionType == SlotActionType.QUICK_CRAFT) {
            if (!slotIsPlayer) {
                ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                ci.cancel();
            }
            return;
        }

        if (!slotIsPlayer) {
            ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
            ci.cancel();
            return;
        }

        if (actionType == SlotActionType.PICKUP_ALL && cursorProtected) {
            ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
            ci.cancel();
        }
    }
}
