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
        boolean slotValid = slotId >= 0 && slotId < this.handler.slots.size();
        boolean slotIsPlayer = slotValid && slot != null && slot.inventory == client.player.getInventory();

        if (actionType == SlotActionType.SWAP) {
            if (button >= 0 && button < 9) {
                ItemStack hotbarStack = client.player.getInventory().getStack(button);
                if (ProtectedItemTags.isProtected(hotbarStack)) {
                    if (!ProtectedItemTags.canRearrange(hotbarStack) || !slotIsPlayer) {
                        ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                        ci.cancel();
                        return;
                    }
                }
            } else if (button == 40) {
                ItemStack offhandStack = client.player.getOffHandStack();
                if (ProtectedItemTags.isProtected(offhandStack)) {
                    if (!ProtectedItemTags.canOffhandSwap(offhandStack) || !ProtectedItemTags.canRearrange(offhandStack) || !slotIsPlayer) {
                        ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                        ci.cancel();
                        return;
                    }
                }
            }
            if (slotProtected) {
                if (button == 40 && (!ProtectedItemTags.canOffhandSwap(slotStack) || !ProtectedItemTags.canRearrange(slotStack))) {
                    ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                    ci.cancel();
                    return;
                }
                if (button >= 0 && button < 9 && !ProtectedItemTags.canRearrange(slotStack)) {
                    ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                    ci.cancel();
                    return;
                }
                if (!slotIsPlayer) {
                    ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                    ci.cancel();
                    return;
                }
            }
        }

        if (!cursorProtected && !slotProtected) {
            return;
        }

        if (!slotValid) {
            if (cursorProtected && !ProtectedItemTags.canDrop(cursorStack)) {
                ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                ci.cancel();
            }
            return;
        }

        if (actionType == SlotActionType.THROW) {
            if ((slotProtected && !ProtectedItemTags.canDrop(slotStack)) || (cursorProtected && !ProtectedItemTags.canDrop(cursorStack))) {
                ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                ci.cancel();
            }
            return;
        }

        if (actionType == SlotActionType.CLONE) {
            ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
            ci.cancel();
            return;
        }

        if (actionType == SlotActionType.QUICK_MOVE) {
            if (slotProtected && (!ProtectedItemTags.canRearrange(slotStack) || !slotIsPlayer)) {
                ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                ci.cancel();
            }
            return;
        }

        if (actionType == SlotActionType.QUICK_CRAFT) {
            if ((cursorProtected && !ProtectedItemTags.canRearrange(cursorStack))
                || (slotProtected && !ProtectedItemTags.canRearrange(slotStack))
                || !slotIsPlayer) {
                ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                ci.cancel();
            }
            return;
        }

        if (actionType == SlotActionType.PICKUP) {
            if ((cursorProtected && !ProtectedItemTags.canRearrange(cursorStack))
                || (slotProtected && !ProtectedItemTags.canRearrange(slotStack))
                || !slotIsPlayer) {
                ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                ci.cancel();
            }
            return;
        }

        if (actionType == SlotActionType.PICKUP_ALL) {
            if (cursorProtected || (slotProtected && !ProtectedItemTags.canRearrange(slotStack))) {
                ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
                ci.cancel();
            }
            return;
        }

        if (!slotIsPlayer) {
            ProtectedItemFeedback.sendRuleBlockedMessage(client.player);
            ci.cancel();
        }
    }
}
