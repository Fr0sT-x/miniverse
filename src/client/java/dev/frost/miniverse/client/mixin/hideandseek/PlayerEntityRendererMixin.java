package dev.frost.miniverse.client.mixin.hideandseek;

import dev.frost.miniverse.client.hideandseek.HideAndSeekDisguiseClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true)
    private void onRenderPlayer(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!HideAndSeekDisguiseClient.isActive()) {
            return;
        }

        BlockState disguise = HideAndSeekDisguiseClient.getDisguise(player.getUuid());
        if (disguise != null) {
            // Cancel player model, skin, limbs, armor, and held items entirely
            ci.cancel();

            // If solidified in the world, the physical block is in the world at this position,
            // so we skip rendering an entity model to avoid duplicate blocks / z-fighting.
            if (HideAndSeekDisguiseClient.isSolidified(player.getUuid())) {
                return;
            }

            matrices.push();

            // Rotate the block 360 degrees smoothly matching the player's mouse look orientation
            float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevYaw, player.getYaw());
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));

            // Center the 1x1 block around the player's center
            matrices.translate(-0.5, 0.0, -0.5);

            MinecraftClient.getInstance().getBlockRenderManager()
                .renderBlockAsEntity(disguise, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);

            matrices.pop();
        }
    }

    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V", at = @At("HEAD"), cancellable = true)
    private void onRenderLabel(AbstractClientPlayerEntity player, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float tickDelta, CallbackInfo ci) {
        if (HideAndSeekDisguiseClient.isActive() && HideAndSeekDisguiseClient.isDisguised(player.getUuid())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderRightArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderRightArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (HideAndSeekDisguiseClient.isActive() && player != null && HideAndSeekDisguiseClient.isDisguised(player.getUuid())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderLeftArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (HideAndSeekDisguiseClient.isActive() && player != null && HideAndSeekDisguiseClient.isDisguised(player.getUuid())) {
            ci.cancel();
        }
    }
}
