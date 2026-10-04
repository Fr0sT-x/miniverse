package dev.frost.miniverse.client.mixin.hideandseek;

import dev.frost.miniverse.client.hideandseek.HideAndSeekDisguiseClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerPushOutOfBlocksMixin {

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(double x, double z, CallbackInfo ci) {
        if (!HideAndSeekDisguiseClient.isActive()) {
            return;
        }
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (HideAndSeekDisguiseClient.isSolidified(self.getUuid())) {
            ci.cancel();
        }
    }
}
