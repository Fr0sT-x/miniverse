package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.core.item.ProtectedItemFeedback;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTags;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class ProtectedItemClientPlayerEntityMixin {
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void miniverse$blockProtectedSelectedItemDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        ItemStack selectedStack = player.getInventory().getMainHandStack();
        if (ProtectedItemTags.isProtected(selectedStack)) {
            ProtectedItemFeedback.sendRuleBlockedMessage(player);
            cir.setReturnValue(false);
        }
    }
}
