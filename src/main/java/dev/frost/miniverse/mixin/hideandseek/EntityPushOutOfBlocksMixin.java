package dev.frost.miniverse.mixin.hideandseek;

import dev.frost.miniverse.minigame.impl.hideandseek.disguise.BlockDisguiseManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityPushOutOfBlocksMixin {

    @Inject(method = "pushOutOfBlocks(DDD)V", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(double x, double y, double z, CallbackInfo ci) {
        if (!BlockDisguiseManager.isHideAndSeekActive()) {
            return;
        }
        Entity self = (Entity) (Object) this;
        if (self instanceof ServerPlayerEntity player) {
            if (BlockDisguiseManager.isPlayerSolidified(player.getUuid())) {
                ci.cancel();
            }
        }
    }
}
