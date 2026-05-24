package dev.frost.miniverse.client.protection;

import dev.frost.miniverse.minigame.core.protection.ProtectionOverlayRenderMode;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

final class ProtectionOverlayRenderLayer {
    private static final Map<ProtectionOverlayRenderMode, Function<Identifier, RenderLayer>> GLOW_LAYERS =
        new EnumMap<>(ProtectionOverlayRenderMode.class);
    private static final Map<ProtectionOverlayRenderMode, Function<Identifier, RenderLayer>> SURFACE_LAYERS =
        new EnumMap<>(ProtectionOverlayRenderMode.class);

    static {
        for (ProtectionOverlayRenderMode mode : ProtectionOverlayRenderMode.values()) {
            GLOW_LAYERS.put(mode, Util.memoize(texture -> create(texture, mode, true)));
            SURFACE_LAYERS.put(mode, Util.memoize(texture -> create(texture, mode, false)));
        }
    }

    private ProtectionOverlayRenderLayer() {
    }

    static RenderLayer glow(Identifier texture, ProtectionOverlayRenderMode mode) {
        return GLOW_LAYERS.get(mode).apply(texture);
    }

    static RenderLayer surface(Identifier texture, ProtectionOverlayRenderMode mode) {
        return SURFACE_LAYERS.get(mode).apply(texture);
    }

    private static RenderLayer create(Identifier texture, ProtectionOverlayRenderMode mode, boolean additive) {
        RenderLayer.MultiPhaseParameters parameters = RenderLayer.MultiPhaseParameters.builder()
            .program(additive ? RenderPhase.ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM : RenderPhase.ENTITY_TRANSLUCENT_PROGRAM)
            .texture(new RenderPhase.Texture(texture, false, false))
            .transparency(additive ? RenderPhase.ADDITIVE_TRANSPARENCY : RenderPhase.TRANSLUCENT_TRANSPARENCY)
            .depthTest(mode == ProtectionOverlayRenderMode.THROUGH_WALLS ? RenderPhase.ALWAYS_DEPTH_TEST : RenderPhase.LEQUAL_DEPTH_TEST)
            .cull(RenderPhase.DISABLE_CULLING)
            .lightmap(RenderPhase.ENABLE_LIGHTMAP)
            .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
            .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
            .writeMaskState(RenderPhase.COLOR_MASK)
            .build(RenderLayer.OutlineMode.NONE);
        return RenderLayer.of(
            "miniverse_protection_" + mode.id() + (additive ? "_glow" : "_surface"),
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS,
            1536,
            true,
            true,
            parameters
        );
    }
}
