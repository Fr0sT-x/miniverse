package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.core.item.ProtectedItemFeedback;
import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ProtectedItemServerPlayerEntityMixin {
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void miniverse$blockProtectedSelectedItemDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ItemStack selectedStack = player.getMainHandStack();
        if (ProtectedItemService.getInstance().shouldCancelDrop(player, selectedStack)) {
            ProtectedItemFeedback.sendRuleBlockedMessage(player);
            player.playerScreenHandler.sendContentUpdates();
            cir.setReturnValue(false);
        }
    }
}
