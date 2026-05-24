package dev.frost.miniverse.client.protection;

import dev.frost.miniverse.minigame.core.protection.ProtectionOverlayRenderMode;
import dev.frost.miniverse.minigame.core.protection.ProtectionOverlaySettings;
import dev.frost.miniverse.minigame.core.protection.ProtectionOverlayStyle;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public final class ProtectionOverlayFeatureRenderer
    extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    private static final float BRIGHTNESS_SCALE = 0.80F;

    public ProtectionOverlayFeatureRenderer(PlayerEntityRenderer renderer) {
        super((FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>) renderer);
    }

    @Override
    public void render(MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light,
                       AbstractClientPlayerEntity player,
                       float limbAngle,
                       float limbDistance,
                       float tickDelta,
                       float animationProgress,
                       float headYaw,
                       float headPitch) {
        ProtectionOverlayClient.ActiveOverlay overlay = ProtectionOverlayClient.getOverlayForPlayer(player.getUuid(), tickDelta);
        if (overlay == null || player.isInvisible()) {
            return;
        }

        Identifier texture = player.getSkinTextures().texture();
        ProtectionOverlaySettings settings = overlay.settings();
        ProtectionOverlayRenderMode mode = settings.renderMode();
        RenderLayer glowLayer = ProtectionOverlayRenderLayer.glow(texture, mode);
        RenderLayer surfaceLayer = ProtectionOverlayRenderLayer.surface(texture, mode);
        float pulse = overlay.animationPulse();
        float alpha = settings.alpha() * overlay.fade() * pulse * BRIGHTNESS_SCALE;
        float intensity = settings.intensity();

        switch (settings.style()) {
            case VANILLA_GLOW -> renderVanillaInspired(matrices, vertexConsumers, player, settings, alpha, intensity, glowLayer, surfaceLayer);
            case SLIM_GLOW -> renderSlimInspired(matrices, vertexConsumers, player, settings, alpha, intensity, glowLayer, surfaceLayer);
            case FILLED_GLOW -> renderFilledInspired(matrices, vertexConsumers, light, player, settings, alpha, intensity, glowLayer, surfaceLayer);
        }
    }

    private void renderVanillaInspired(MatrixStack matrices,
                                       VertexConsumerProvider vertexConsumers,
                                       AbstractClientPlayerEntity player,
                                       ProtectionOverlaySettings settings,
                                       float alpha,
                                       float intensity,
                                       RenderLayer glowLayer,
                                       RenderLayer surfaceLayer) {
        renderPass(matrices, vertexConsumers, player, surfaceLayer, settings.glowColor(), alpha * 0.20F, 1.012F, FULL_BRIGHT_LIGHT);
        renderPass(matrices, vertexConsumers, player, glowLayer, settings.outlineColor(), alpha * 0.68F, 1.026F, FULL_BRIGHT_LIGHT);
        renderPass(matrices, vertexConsumers, player, glowLayer, settings.glowColor(), alpha * 0.34F * intensity, 1.070F, FULL_BRIGHT_LIGHT);
    }

    private void renderSlimInspired(MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers,
                                    AbstractClientPlayerEntity player,
                                    ProtectionOverlaySettings settings,
                                    float alpha,
                                    float intensity,
                                    RenderLayer glowLayer,
                                    RenderLayer surfaceLayer) {
        renderPass(matrices, vertexConsumers, player, surfaceLayer, settings.glowColor(), alpha * 0.16F, 1.010F, FULL_BRIGHT_LIGHT);
        renderPass(matrices, vertexConsumers, player, glowLayer, settings.outlineColor(), alpha * 0.78F, 1.022F, FULL_BRIGHT_LIGHT);
        renderPass(matrices, vertexConsumers, player, glowLayer, settings.glowColor(), alpha * 0.30F * intensity, 1.040F, FULL_BRIGHT_LIGHT);
    }

    private void renderFilledInspired(MatrixStack matrices,
                                      VertexConsumerProvider vertexConsumers,
                                      int light,
                                      AbstractClientPlayerEntity player,
                                      ProtectionOverlaySettings settings,
                                      float alpha,
                                      float intensity,
                                      RenderLayer glowLayer,
                                      RenderLayer surfaceLayer) {
        renderPass(matrices, vertexConsumers, player, surfaceLayer, settings.glowColor(), alpha * 0.46F, 1.010F, light);
        renderPass(matrices, vertexConsumers, player, glowLayer, settings.glowColor(), alpha * 0.22F * intensity, 1.028F, FULL_BRIGHT_LIGHT);
    }

    private void renderPass(MatrixStack matrices,
                            VertexConsumerProvider vertexConsumers,
                            AbstractClientPlayerEntity player,
                            RenderLayer layer,
                            int rgbColor,
                            float alpha,
                            float scale,
                            int light) {
        int color = composeColor(rgbColor, alpha);
        if (((color >>> 24) & 0xFF) <= 2) {
            return;
        }

        matrices.push();
        matrices.scale(scale, scale, scale);
        this.getContextModel().render(
            matrices,
            vertexConsumers.getBuffer(layer),
            light,
            OverlayTexture.DEFAULT_UV,
            color
        );
        matrices.pop();
    }

    private int composeColor(int rgbColor, float alpha) {
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return (a << 24) | (rgbColor & 0x00FFFFFF);
    }
}
