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
    private static final float REGION_A = 0.55F;

    public static int getMarkerColor(String definitionKey) {
        int hash = definitionKey.hashCode();
        float hue = Math.abs(hash % 360) / 360.0f;
        return java.awt.Color.HSBtoRGB(hue, 0.8f, 0.9f) & 0xFFFFFF;
    }

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

            // Draw Gizmo
            if (!state.clipboard.isEmpty()) {
                Camera camera = context.camera();
                Vec3d cameraPos = camera.getPos();
                MatrixStack matrices = context.matrixStack();

                matrices.push();
                matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

                double cx = state.selectionCenterX + state.transX;
                double cy = state.selectionCenterY + state.transY;
                double cz = state.selectionCenterZ + state.transZ;

                var tessellator = Tessellator.getInstance();

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);

                int colorX = state.hoveredAxis == 1 ? 0xFFFFAAAA : 0xFFFF0000;
                int colorY = state.hoveredAxis == 2 ? 0xFFAAFFAA : 0xFF00FF00;
                int colorZ = state.hoveredAxis == 3 ? 0xFFAAAAFF : 0xFF0000FF;

                if (state.gizmoMode == 0) { // Translate
                    GizmoRenderer.drawCylinder(matrices, tessellator, cx, cy, cz, 2.0, 0.05, 8, colorX, 1, 0, 0);
                    GizmoRenderer.drawCone(matrices, tessellator, cx + 2.0, cy, cz, 0.5, 0.15, 8, colorX, 1, 0, 0);

                    GizmoRenderer.drawCylinder(matrices, tessellator, cx, cy, cz, 2.0, 0.05, 8, colorY, 0, 1, 0);
                    GizmoRenderer.drawCone(matrices, tessellator, cx, cy + 2.0, cz, 0.5, 0.15, 8, colorY, 0, 1, 0);

                    GizmoRenderer.drawCylinder(matrices, tessellator, cx, cy, cz, 2.0, 0.05, 8, colorZ, 0, 0, 1);
                    GizmoRenderer.drawCone(matrices, tessellator, cx, cy, cz + 2.0, 0.5, 0.15, 8, colorZ, 0, 0, 1);
                } else if (state.gizmoMode == 1) { // Rotate (Simple Circle)
                    // We only implement Y rotation for now, maybe draw a torus or simple circle
                    GizmoRenderer.drawCylinder(matrices, tessellator, cx - 1, cy, cz - 1, 2.0, 0.05, 8, colorY, 1, 0, 0);
                    GizmoRenderer.drawCylinder(matrices, tessellator, cx - 1, cy, cz + 1, 2.0, 0.05, 8, colorY, 1, 0, 0);
                    GizmoRenderer.drawCylinder(matrices, tessellator, cx - 1, cy, cz - 1, 2.0, 0.05, 8, colorY, 0, 0, 1);
                    GizmoRenderer.drawCylinder(matrices, tessellator, cx + 1, cy, cz - 1, 2.0, 0.05, 8, colorY, 0, 0, 1);
                } else if (state.gizmoMode == 2) { // Scale
                    GizmoRenderer.drawCylinder(matrices, tessellator, cx, cy, cz, 2.0, 0.05, 8, colorX, 1, 0, 0);
                    GizmoRenderer.drawCylinder(matrices, tessellator, cx, cy, cz, 2.0, 0.05, 8, colorY, 0, 1, 0);
                    GizmoRenderer.drawCylinder(matrices, tessellator, cx, cy, cz, 2.0, 0.05, 8, colorZ, 0, 0, 1);
                }

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
        
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((drawContext, tickDeltaManager) -> {
            MapEditorState state = MapEditorState.INSTANCE;
            if (!state.editorActive || dev.frost.miniverse.client.MiniverseClient.isScreenshotPending()) return;

            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client.world == null || client.player == null || client.getWindow() == null || lastProjMatrix == null || lastModelViewMatrix == null) return;
            
            int screenW = client.getWindow().getScaledWidth();
            int screenH = client.getWindow().getScaledHeight();
            int cx = screenW / 2;
            int cy = screenH / 2;

            class HoveredMarker {
                final SessionSnapshotData.EditorMarker marker;
                final SessionSnapshotData.EditorMarkerDefinition def;
                final SessionSnapshotData.EditorExtension ext;
                final double worldDist;
                HoveredMarker(SessionSnapshotData.EditorMarker marker, SessionSnapshotData.EditorMarkerDefinition def, SessionSnapshotData.EditorExtension ext, double worldDist) {
                    this.marker = marker; this.def = def; this.ext = ext; this.worldDist = worldDist;
                }
            }
            java.util.List<HoveredMarker> hovered = new java.util.ArrayList<>();

            net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
            net.minecraft.util.math.Vec3d camPos = camera.getPos();

            for (SessionSnapshotData.EditorExtension extension : SessionSnapshotData.editorExtensions()) {
                for (SessionSnapshotData.EditorMarkerDefinition def : extension.markers()) {
                    if (!state.isOverlayEnabled(extension.gameId(), def.key())) continue;

                    List<SessionSnapshotData.EditorMarker> markers = SessionSnapshotData.editorState().markers(extension.gameId(), def.key());
                    if (markers == null || markers.isEmpty()) continue;

                    for (SessionSnapshotData.EditorMarker marker : markers) {
                        if (state.hiddenIndividualMarkers.contains(marker.id())) continue;

                        double px, py, pz;
                        if ("REGION".equalsIgnoreCase(marker.type()) && marker.regions() != null && !marker.regions().isEmpty()) {
                            SessionSnapshotData.EditorRegionPart part = marker.regions().get(0);
                            px = (Math.floor(part.min().x()) + Math.floor(part.max().x()) + 1.0) / 2.0;
                            py = Math.floor(Math.max(part.min().y(), part.max().y())) + 1.0;
                            pz = (Math.floor(part.min().z()) + Math.floor(part.max().z()) + 1.0) / 2.0;
                        } else if (marker.points() != null && !marker.points().isEmpty()) {
                            px = Math.floor(marker.points().get(0).x()) + 0.5;
                            py = Math.floor(marker.points().get(0).y()); // exact top face
                            pz = Math.floor(marker.points().get(0).z()) + 0.5;
                        } else continue;

                        double wx = px - camPos.x;
                        double wy = py - camPos.y;
                        double wz = pz - camPos.z;
                        double worldDist = Math.sqrt(wx*wx + wy*wy + wz*wz);

                        org.joml.Vector4f clip = new org.joml.Vector4f((float) wx, (float) wy, (float) wz, 1.0f);
                        lastModelViewMatrix.transform(clip);
                        lastProjMatrix.transform(clip);
                        if (clip.w <= 0.0f) continue;

                        float ndcX = clip.x / clip.w;
                        float ndcY = clip.y / clip.w;
                        if (Math.abs(ndcX) > 1.2f || Math.abs(ndcY) > 1.2f) continue;

                        int sx = (int) ((ndcX * 0.5f + 0.5f) * screenW);
                        int sy = (int) ((0.5f - ndcY * 0.5f) * screenH);

                        double distSq = (sx - cx) * (sx - cx) + (sy - cy) * (sy - cy);
                        if (distSq < 400) { // tight 20px radius
                            hovered.add(new HoveredMarker(marker, def, extension, worldDist));
                        }
                    }
                }
            }

            if (!hovered.isEmpty()) {
                hovered.sort(java.util.Comparator.comparingDouble(h -> h.worldDist));

                int boxX = cx + 15;
                int boxY = cy + 15;
                int padding = 6;
                int lineHeight = 10;
                
                int width = 0;
                int height = padding * 2;
                
                java.util.List<String> renderedLines = new java.util.ArrayList<>();
                java.util.List<Integer> lineColors = new java.util.ArrayList<>();

                for (HoveredMarker h : hovered) {
                    String title = h.ext.displayName() + " \u2192 " + h.def.displayName();
                    String nameLine = h.marker.name();
                    String distLine = "Distance: " + String.format("%.1f", h.worldDist) + "m";
                    String coordLine = "";
                    if ("REGION".equalsIgnoreCase(h.marker.type()) && h.marker.regions() != null && !h.marker.regions().isEmpty()) {
                        SessionSnapshotData.EditorRegionPart p = h.marker.regions().get(0);
                        coordLine = "Min: (" + Math.round(p.min().x()) + ", " + Math.round(p.min().y()) + ", " + Math.round(p.min().z()) + ") Max: (" + Math.round(p.max().x()) + ", " + Math.round(p.max().y()) + ", " + Math.round(p.max().z()) + ")";
                    } else if (h.marker.points() != null && !h.marker.points().isEmpty()) {
                        SessionSnapshotData.EditorPoint p = h.marker.points().get(0);
                        coordLine = "Coords: (" + Math.round(p.x()) + ", " + Math.round(p.y()) + ", " + Math.round(p.z()) + ")";
                    }
                    
                    renderedLines.add(title); lineColors.add(getMarkerColor(h.marker.definitionKey()));
                    renderedLines.add(nameLine); lineColors.add(0xFFEEEEEE);
                    renderedLines.add(distLine); lineColors.add(0xFFAAAAAA);
                    renderedLines.add(coordLine); lineColors.add(0xFF777777);
                    renderedLines.add(""); lineColors.add(0);
                }
                
                if (!renderedLines.isEmpty()) {
                    renderedLines.remove(renderedLines.size() - 1);
                    lineColors.remove(lineColors.size() - 1);
                }

                for (String l : renderedLines) {
                    width = Math.max(width, client.textRenderer.getWidth(l));
                }
                height += renderedLines.size() * lineHeight;

                drawContext.fill(boxX, boxY, boxX + width + (padding * 2), boxY + height, 0xCC000000);
                drawContext.drawBorder(boxX, boxY, width + (padding * 2), height, getMarkerColor(hovered.get(0).marker.definitionKey()) | 0xFF000000);
                
                int textY = boxY + padding;
                for (int i = 0; i < renderedLines.size(); i++) {
                    String text = renderedLines.get(i);
                    int color = lineColors.get(i);
                    if (!text.isEmpty()) {
                        drawContext.drawText(client.textRenderer, net.minecraft.text.Text.literal(text).withColor(color), boxX + padding, textY, 0xFFFFFFFF, false);
                    }
                    textY += lineHeight;
                }
            }
        });
    }

    /** Captured each frame from WorldRenderContext for use in the HUD projection. */
    private static org.joml.Matrix4f lastProjMatrix = null;
    private static org.joml.Matrix4f lastModelViewMatrix = null;

    private static void drawSpawnPoint(MatrixStack matrices, Tessellator tessellator, SessionSnapshotData.EditorMarker marker) {
        SessionSnapshotData.EditorPoint p = marker.points().get(0);
        float x = (float) Math.floor(p.x());
        float y = (float) p.y() + 0.001F;
        float z = (float) Math.floor(p.z());

        int color = getMarkerColor(marker.definitionKey());
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = 0.85F;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        var buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x, y, z).color(r, g, b, a);
        buffer.vertex(matrix, x, y, z + 1.0F).color(r, g, b, a);
        buffer.vertex(matrix, x + 1.0F, y, z + 1.0F).color(r, g, b, a);
        buffer.vertex(matrix, x + 1.0F, y, z).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawRegion(MatrixStack matrices, Tessellator tessellator, SessionSnapshotData.EditorMarker marker) {
        int baseColor = getMarkerColor(marker.definitionKey());
        float r = ((baseColor >> 16) & 0xFF) / 255.0F;
        float g = ((baseColor >> 8) & 0xFF) / 255.0F;
        float b = (baseColor & 0xFF) / 255.0F;
        
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
