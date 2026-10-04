package dev.frost.miniverse.client.mixin.hideandseek;

import dev.frost.miniverse.client.hideandseek.HideAndSeekDisguiseClient;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class ClientPlayerDimensionsMixin {

    @Unique
    private static final EntityDimensions DISGUISED_DIMENSIONS = EntityDimensions.fixed(0.6F, 0.85F).withEyeHeight(0.75F);
    @Unique
    private static final EntityDimensions DISGUISED_SNEAKING_DIMENSIONS = EntityDimensions.fixed(0.6F, 0.75F).withEyeHeight(0.65F);
    @Unique
    private static final EntityDimensions SOLIDIFIED_DIMENSIONS = EntityDimensions.fixed(1.0F, 1.0F).withEyeHeight(1.2F);

    @Inject(method = "getBaseDimensions", at = @At("HEAD"), cancellable = true)
    private void onGetBaseDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!HideAndSeekDisguiseClient.isActive()) {
            return;
        }

        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld() != null && !self.getWorld().isClient()) {
            return;
        }

        if (HideAndSeekDisguiseClient.isDisguised(self.getUuid())) {
            if (HideAndSeekDisguiseClient.isSolidified(self.getUuid())) {
                cir.setReturnValue(SOLIDIFIED_DIMENSIONS);
            } else if (pose == EntityPose.CROUCHING) {
                cir.setReturnValue(DISGUISED_SNEAKING_DIMENSIONS);
            } else {
                cir.setReturnValue(DISGUISED_DIMENSIONS);
            }
        }
    }
}
