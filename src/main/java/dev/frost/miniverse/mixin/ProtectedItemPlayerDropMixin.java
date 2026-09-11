package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.core.item.ProtectedItemFeedback;
import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class ProtectedItemPlayerDropMixin {
    private static final ThreadLocal<Boolean> RETURNING_ITEM = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/entity/ItemEntity;",
        at = @At("HEAD"), cancellable = true)
    private void miniverse$blockProtectedItemDrop(ItemStack stack, boolean throwRandomly, CallbackInfoReturnable<ItemEntity> cir) {
        if (RETURNING_ITEM.get()) {
            return;
        }
        if (!((Object) this instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (ProtectedItemService.getInstance().shouldCancelDrop(serverPlayer, stack)) {
            ProtectedItemFeedback.sendRuleBlockedMessage(serverPlayer);
            RETURNING_ITEM.set(Boolean.TRUE);
            try {
                serverPlayer.getInventory().offerOrDrop(stack);
            } finally {
                RETURNING_ITEM.set(Boolean.FALSE);
            }
            cir.setReturnValue(null);
        }
    }
}

