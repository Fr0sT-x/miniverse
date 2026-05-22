package dev.frost.miniverse.client.mixin;

import dev.frost.miniverse.client.transition.TransitionOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class ConnectScreenTransitionOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void miniverse$renderTransitionOverlay(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (TransitionOverlay.isActive()) {
            TransitionOverlay.renderOnScreen(context);
            ci.cancel();
        }
    }
}
