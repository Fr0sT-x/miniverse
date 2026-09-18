package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.core.freeze.DownedPlayerTracker;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class DownedPlayerPoseMixin {
    @Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
    private void miniverse$forceDownedCrawlPose(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (DownedPlayerTracker.isDowned(self.getUuid())) {
            if (!self.isSpectator() && self.isAlive()) {
                self.setPose(EntityPose.SWIMMING);
                ci.cancel();
            }
        }
    }
}
