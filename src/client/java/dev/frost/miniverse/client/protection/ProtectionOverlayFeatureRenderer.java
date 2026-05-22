package dev.frost.miniverse.client.protection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature renderer for translucent player protection overlays.
 *
 * Renders a soft, translucent aura/shimmer effect over protected players.
 * Uses proper depth testing and blending to:
 * - Preserve player skin visibility underneath
 * - Hide naturally behind walls via GPU depth buffer
 * - Avoid manual line-of-sight calculations
 * - Eliminate flickering and rendering artifacts
 *
 * The overlay is rendered as an expanded model with low alpha transparency,
 * simulating a shield aura or energy layer effect.
 */
@SuppressWarnings("unchecked")
public final class ProtectionOverlayFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final float OVERLAY_SCALE_EXPANSION = 1.01F;
    private static final float OUTLINE_SCALE_EXPANSION = 1.028F;
    private static final float BORDER_SCALE_EXPANSION = 1.045F;
    private static final int MAX_OVERLAY_ALPHA = 0x88; // Subtle overlay so the skin stays readable.
    private static final int MAX_OUTLINE_ALPHA = 0x8C; // Subtle but readable outline.
    private static final int MIN_OUTLINE_ALPHA = 0x1A; // Prevents barely-visible outlines.
    private static final int BORDER_ALPHA = 0xA0; // Dark outline border strength.
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    private static final Map<Identifier, RenderLayer> OUTLINE_LAYERS = new ConcurrentHashMap<>();

    public ProtectionOverlayFeatureRenderer(PlayerEntityRenderer<?> renderer) {
        super((FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) renderer);
    }

    @Override
    public void render(MatrixStack matrices,
                       OrderedRenderCommandQueue queue,
                       int light,
                       PlayerEntityRenderState state,
                       float limbAngle,
                       float limbDistance) {
        // Look up the actual entity to get its UUID
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        var entity = client.world.getEntityById(state.id);
        if (entity == null) {
            return;
        }

        // Check if player has an active overlay by UUID
        ProtectionOverlayClient.ProtectionOverlayState overlay = ProtectionOverlayClient.getOverlayForPlayer(entity.getUuid());
        if (overlay == null || state.invisible || state.skinTextures == null) {
            return;
        }

        renderGlowOutline(matrices, queue, state, overlay);
        renderTranslucentOverlay(matrices, queue, light, state, overlay);
    }

    private void renderGlowOutline(MatrixStack matrices,
                                   OrderedRenderCommandQueue queue,
                                   PlayerEntityRenderState state,
                                   ProtectionOverlayClient.ProtectionOverlayState overlay) {
        int argb = overlay.argbColor();
        int a = (argb >>> 24) & 0xFF;
        if (a < 3) {
            return;
        }

        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int outlineAlpha = Math.min(MAX_OUTLINE_ALPHA, Math.max(MIN_OUTLINE_ALPHA, a));
        int outlineColor = (outlineAlpha << 24) | (r << 16) | (g << 8) | b;
        int borderColor = (BORDER_ALPHA << 24); // Solid dark border around silhouette.

        RenderLayer outlineLayer = getOutlineLayer(state.skinTextures.body().texturePath());
        renderScaledPass(matrices, queue, FULL_BRIGHT_LIGHT, state, borderColor, BORDER_SCALE_EXPANSION, outlineLayer);
        renderScaledPass(matrices, queue, FULL_BRIGHT_LIGHT, state, outlineColor, OUTLINE_SCALE_EXPANSION, outlineLayer);
    }

    /**
     * Renders the translucent overlay layer using proper blending and depth testing.
     */
    private void renderTranslucentOverlay(MatrixStack matrices,
                                          OrderedRenderCommandQueue queue,
                                          int light,
                                          PlayerEntityRenderState state,
                                          ProtectionOverlayClient.ProtectionOverlayState overlay) {
        int argb = overlay.argbColor();
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        if (a < 3) {
            return;
        }

        int overlayColor = (Math.min(a, MAX_OVERLAY_ALPHA) << 24) | (r << 16) | (g << 8) | b;
        renderScaledPass(matrices, queue, light, state, overlayColor, OVERLAY_SCALE_EXPANSION,
            RenderLayers.entityTranslucent(state.skinTextures.body().texturePath(), false));
    }

    private void renderScaledPass(MatrixStack matrices,
                                  OrderedRenderCommandQueue queue,
                                  int light,
                                  PlayerEntityRenderState state,
                                  int color,
                                  float scale,
                                  RenderLayer layer) {
        matrices.push();
        matrices.scale(scale, scale, scale);

        queue.submitModel(
            this.getContextModel(),
            state,
            matrices,
            layer,
            light,
            OverlayTexture.DEFAULT_UV,
            color,
            null,
            0,
            null
        );

        matrices.pop();
    }

    private static RenderLayer getOutlineLayer(Identifier texture) {
        return OUTLINE_LAYERS.computeIfAbsent(texture, key -> {
            RenderSetup setup = RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
                .texture("Sampler0", key)
                .useLightmap()
                .useOverlay()
                .crumbling()
                .translucent()
                .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING_FORWARD)
                .outlineMode(RenderSetup.OutlineMode.NONE)
                .build();

            return RenderLayer.of("miniverse_protection_outline", setup);
        });
    }
}
