package dev.frost.miniverse.client.gui.map;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders solid colored overlays for Map Editor markers in the 3D world.
 * <p>
 * POINT markers render a solid colored quad on the top face of the block.
 * REGION markers render a horizontal colored plane at the top Y level of the region.
 * <p>
 * Only renders when the map editor is active (editorActive flag) and
 * per-definition overlay toggles are enabled.
 */
public final class MapEditorOverlayClient {
    private static final float SPAWN_R = 0.6F, SPAWN_G = 0.1F, SPAWN_B = 0.9F, SPAWN_A = 0.85F; // Purple for spawns
    private static final float REGION_R = 1.0F, REGION_G = 0.85F, REGION_B = 0.0F, REGION_A = 0.55F; // Yellow for regions

    private MapEditorOverlayClient() {
    }

    public static void register() {
        WorldRenderEvents.LAST.register(context -> {
            // Capture matrices for HUD point projection
            lastProjMatrix = new org.joml.Matrix4f(context.projectionMatrix());
            lastModelViewMatrix = new org.joml.Matrix4f(context.positionMatrix());

            MapEditorState state = MapEditorState.INSTANCE;

            // Only render when map editor is active (on a map editor server)
            if (!state.editorActive || dev.frost.miniverse.client.MiniverseClient.isScreenshotPending()) {
                return;
            }

            // Render all enabled overlay definitions
            for (SessionSnapshotData.EditorExtension extension : SessionSnapshotData.editorExtensions()) {
                for (SessionSnapshotData.EditorMarkerDefinition def : extension.markers()) {
                    if (!state.isOverlayEnabled(extension.gameId(), def.key())) continue;

                    List<SessionSnapshotData.EditorMarker> markers = SessionSnapshotData.editorState().markers(extension.gameId(), def.key());
                    if (markers == null || markers.isEmpty()) continue;

                Camera camera = context.camera();
                Vec3d cameraPos = camera.getPos();
                MatrixStack matrices = context.matrixStack();

                matrices.push();
                matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

                var tessellator = Tessellator.getInstance();

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);

                for (SessionSnapshotData.EditorMarker marker : markers) {
                    if (state.hiddenIndividualMarkers.contains(marker.id())) {
                        continue;
                    }
                    if ("REGION".equalsIgnoreCase(marker.type())) {
                        if (marker.regions() != null && !marker.regions().isEmpty()) {
                            drawRegion(matrices, tessellator, marker);
                        }
                    } else if (!marker.points().isEmpty()) {
                        drawSpawnPoint(matrices, tessellator, marker);
                    }
                }

                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.disableBlend();

                matrices.pop();
                }
            }

            // Draw the unsaved builder selection if present
            if (!state.currentBuilderSelection.isEmpty()) {
                Camera camera = context.camera();
                Vec3d cameraPos = camera.getPos();
                MatrixStack matrices = context.matrixStack();

                matrices.push();
                matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

                var tessellator = Tessellator.getInstance();

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);

                SessionSnapshotData.EditorMarker builderMarker = new SessionSnapshotData.EditorMarker(
                    "builder", "builder", "builder", "REGION", List.of(), state.currentBuilderSelection, null
                );
                drawRegion(matrices, tessellator, builderMarker);

                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.disableBlend();

                matrices.pop();
            }
        });

        // 2D HUD indicator for active point placements (no particles - screen-space only)
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((drawContext, tickDeltaManager) -> {
            MapEditorState state = MapEditorState.INSTANCE;
            if (!state.editorActive || state.placementPoints.isEmpty()) {
                return;
            }
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client.world == null || client.player == null || client.getWindow() == null
                    || lastProjMatrix == null || lastModelViewMatrix == null) {
                return;
            }
            int screenW = client.getWindow().getScaledWidth();
            int screenH = client.getWindow().getScaledHeight();
            net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
            net.minecraft.util.math.Vec3d camPos = camera.getPos();
            int index = 1;
            for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint p : state.placementPoints) {
                double wx = p.x() - camPos.x;
                double wy = p.y() - camPos.y;
                double wz = p.z() - camPos.z;
                org.joml.Vector4f clip = new org.joml.Vector4f((float) wx, (float) wy, (float) wz, 1.0f);
                // Apply model-view then projection
                lastModelViewMatrix.transform(clip);
                lastProjMatrix.transform(clip);
                if (clip.w <= 0.0f) { index++; continue; }
                float ndcX = clip.x / clip.w;
                float ndcY = clip.y / clip.w;
                if (Math.abs(ndcX) > 1.5f || Math.abs(ndcY) > 1.5f) { index++; continue; }
                int sx = (int) ((ndcX * 0.5f + 0.5f) * screenW);
                int sy = (int) ((0.5f - ndcY * 0.5f) * screenH);
                sx = Math.max(20, Math.min(screenW - 20, sx));
                sy = Math.max(20, Math.min(screenH - 20, sy));
                // Outer ring (purple border), inner fill
                int dotColor = 0xFFB46AFF;
                drawContext.fill(sx - 4, sy - 4, sx + 4, sy + 4, dotColor);
                drawContext.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xFF6622AA);
                // Index and coordinate labels
                String label = "#" + index;
                String coords = "(" + Math.round(p.x()) + ", " + Math.round(p.y()) + ", " + Math.round(p.z()) + ")";
                drawContext.drawText(client.textRenderer, net.minecraft.text.Text.literal(label), sx + 7, sy - 9, dotColor, true);
                drawContext.drawText(client.textRenderer, net.minecraft.text.Text.literal(coords), sx + 7, sy + 1, 0xFFCCCCCC, true);
                index++;
            }
        });
    }

    /** Captured each frame from WorldRenderContext for use in the HUD projection. */
    private static org.joml.Matrix4f lastProjMatrix = null;
    private static org.joml.Matrix4f lastModelViewMatrix = null;

    private static void drawSpawnPoint(MatrixStack matrices, Tessellator tessellator, SessionSnapshotData.EditorMarker marker) {
        SessionSnapshotData.EditorPoint p = marker.points().get(0);
        // The point position is where the player would stand (on top of the block).
        // Render the overlay AT the stored Y coordinate (which is the block top surface).
        float x = (float) Math.floor(p.x());
        float y = (float) p.y() + 0.001F; // Slightly above to avoid z-fighting
        float z = (float) Math.floor(p.z());

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        var buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x, y, z).color(SPAWN_R, SPAWN_G, SPAWN_B, SPAWN_A);
        buffer.vertex(matrix, x, y, z + 1.0F).color(SPAWN_R, SPAWN_G, SPAWN_B, SPAWN_A);
        buffer.vertex(matrix, x + 1.0F, y, z + 1.0F).color(SPAWN_R, SPAWN_G, SPAWN_B, SPAWN_A);
        buffer.vertex(matrix, x + 1.0F, y, z).color(SPAWN_R, SPAWN_G, SPAWN_B, SPAWN_A);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawRegion(MatrixStack matrices, Tessellator tessellator, SessionSnapshotData.EditorMarker marker) {
        float r = REGION_R;
        float g = REGION_G;
        float b = REGION_B;
        
        if (marker.properties() != null && marker.properties().has("restrictions")) {
            com.google.gson.JsonArray arr = marker.properties().getAsJsonArray("restrictions");
            if (!arr.isEmpty()) {
                try {
                    dev.frost.miniverse.minigame.core.region.RegionRestriction res = dev.frost.miniverse.minigame.core.region.RegionRestriction.valueOf(arr.get(0).getAsString());
                    int color = res.color();
                    r = ((color >> 16) & 0xFF) / 255.0F;
                    g = ((color >> 8) & 0xFF) / 255.0F;
                    b = (color & 0xFF) / 255.0F;
                } catch (Exception ignored) {}
            }
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float sideA = REGION_A * 0.4F;

        for (SessionSnapshotData.EditorRegionPart part : marker.regions()) {
            SessionSnapshotData.EditorPoint p1 = part.min();
            SessionSnapshotData.EditorPoint p2 = part.max();

            float minX = (float) Math.floor(Math.min(p1.x(), p2.x()));
            float minY = (float) Math.floor(Math.min(p1.y(), p2.y()));
            float minZ = (float) Math.floor(Math.min(p1.z(), p2.z()));
            float maxX = (float) Math.floor(Math.max(p1.x(), p2.x())) + 1.0F;
            float maxY = (float) Math.floor(Math.max(p1.y(), p2.y())) + 1.0F;
            float maxZ = (float) Math.floor(Math.max(p1.z(), p2.z())) + 1.0F;

            float rMinX = minX - 0.001F;
            float rMinZ = minZ - 0.001F;
            float rMaxX = maxX + 0.001F;
            float rMaxZ = maxZ + 0.001F;

            // Draw top face (horizontal plane at maxY)
            float topY = maxY + 0.001F;
            var buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, rMinX, topY, rMinZ).color(r, g, b, REGION_A);
            buffer.vertex(matrix, rMinX, topY, rMaxZ).color(r, g, b, REGION_A);
            buffer.vertex(matrix, rMaxX, topY, rMaxZ).color(r, g, b, REGION_A);
            buffer.vertex(matrix, rMaxX, topY, rMinZ).color(r, g, b, REGION_A);
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            // Draw bottom face (horizontal plane at minY)
            float bottomY = minY - 0.001F;
            buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, rMinX, bottomY, rMinZ).color(r, g, b, REGION_A);
            buffer.vertex(matrix, rMaxX, bottomY, rMinZ).color(r, g, b, REGION_A);
            buffer.vertex(matrix, rMaxX, bottomY, rMaxZ).color(r, g, b, REGION_A);
            buffer.vertex(matrix, rMinX, bottomY, rMaxZ).color(r, g, b, REGION_A);
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            // North face (minZ)
            buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, rMinX, bottomY, rMinZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMinX, topY, rMinZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMaxX, topY, rMinZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMaxX, bottomY, rMinZ).color(r, g, b, sideA);
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            // South face (maxZ)
            buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, rMinX, bottomY, rMaxZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMaxX, bottomY, rMaxZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMaxX, topY, rMaxZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMinX, topY, rMaxZ).color(r, g, b, sideA);
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            // West face (minX)
            buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, rMinX, bottomY, rMinZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMinX, bottomY, rMaxZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMinX, topY, rMaxZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMinX, topY, rMinZ).color(r, g, b, sideA);
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            // East face (maxX)
            buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, rMaxX, bottomY, rMinZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMaxX, topY, rMinZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMaxX, topY, rMaxZ).color(r, g, b, sideA);
            buffer.vertex(matrix, rMaxX, bottomY, rMaxZ).color(r, g, b, sideA);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
    }
}
