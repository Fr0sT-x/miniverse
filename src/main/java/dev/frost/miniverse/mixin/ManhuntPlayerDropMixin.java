package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.impl.manhunt.ManhuntTrackerProtection;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class ManhuntPlayerDropMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/entity/ItemEntity;",
        at = @At("HEAD"), cancellable = true)
    private void miniverse$blockTrackerDrop(ItemStack stack, boolean throwRandomly, CallbackInfoReturnable<ItemEntity> cir) {
        if (!((Object) this instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (!ManhuntTrackerProtection.isHunter(serverPlayer)) {
            return;
        }
        if (ManhuntTrackerProtection.isTracker(stack)) {
            cir.setReturnValue(null);
        }
    }
}

