package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.core.item.ProtectedItemFeedback;
import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ProtectedItemScreenHandlerMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void miniverse$validateProtectedItems(int slotId, int button, SlotActionType actionType, PlayerEntity player,
                                                  CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (ProtectedItemService.getInstance().shouldCancelInventoryAction(serverPlayer,
            (ScreenHandler) (Object) this, slotId, button, actionType)) {
            ProtectedItemFeedback.sendRuleBlockedMessage(serverPlayer);
            serverPlayer.currentScreenHandler.syncState();
            ci.cancel();
        }
    }
}
