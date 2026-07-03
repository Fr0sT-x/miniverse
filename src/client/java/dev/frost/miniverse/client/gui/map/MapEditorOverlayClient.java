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
            if (!state.currentBuilderSelection.isEmpty() && !state.pastePreviewActive) {
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

            if (state.pastePreviewActive && !state.clipboard.isEmpty()) {
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

                for (SessionSnapshotData.EditorMarker marker : transformedClipboardMarkers(state)) {
                    if ("REGION".equalsIgnoreCase(marker.type())) {
                        if (marker.regions() != null && !marker.regions().isEmpty()) {
                            drawRegion(matrices, tessellator, marker);
                        }
                    } else if (marker.points() != null && !marker.points().isEmpty()) {
                        drawSpawnPoint(matrices, tessellator, marker);
                    }
                }

                SessionSnapshotData.EditorRegionPart bounds = transformedClipboardBounds(state);
                if (bounds != null) {
                    SessionSnapshotData.EditorMarker boundsMarker = new SessionSnapshotData.EditorMarker(
                            "clipboard_bounds", "clipboard_bounds", "Clipboard Bounds", "REGION", List.of(), List.of(bounds), null
                    );
                    drawRegion(matrices, tessellator, boundsMarker);
                }

                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.disableBlend();

                matrices.pop();
            }

            // Draw Gizmo
            if (state.pastePreviewActive && !state.clipboard.isEmpty()) {
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

                int colorX = (state.hoveredTarget == MapEditorState.GizmoTarget.TRANSLATE_X || state.hoveredTarget == MapEditorState.GizmoTarget.SCALE_X) ? 0xFFFFAAAA : 0xFFFF0000;
                int colorY = (state.hoveredTarget == MapEditorState.GizmoTarget.TRANSLATE_Y || state.hoveredTarget == MapEditorState.GizmoTarget.SCALE_Y || state.hoveredTarget == MapEditorState.GizmoTarget.ROTATE_Y) ? 0xFFAAFFAA : 0xFF00FF00;
                int colorZ = (state.hoveredTarget == MapEditorState.GizmoTarget.TRANSLATE_Z || state.hoveredTarget == MapEditorState.GizmoTarget.SCALE_Z) ? 0xFFAAAAFF : 0xFF0000FF;

                int colorXY = state.hoveredTarget == MapEditorState.GizmoTarget.TRANSLATE_XY ? 0x88AAAAFF : 0x440000FF;
                int colorYZ = state.hoveredTarget == MapEditorState.GizmoTarget.TRANSLATE_YZ ? 0x88FFAAAA : 0x44FF0000;
                int colorZX = state.hoveredTarget == MapEditorState.GizmoTarget.TRANSLATE_ZX ? 0x88AAFFAA : 0x4400FF00;
                
                int colorCenter = (state.hoveredTarget == MapEditorState.GizmoTarget.TRANSLATE_XYZ || state.hoveredTarget == MapEditorState.GizmoTarget.SCALE_XYZ) ? 0xFFFFFFFF : 0xFFAAAAAA;

                GizmoRenderer.drawSolidCube(matrices, tessellator, cx - 0.2, cy - 0.2, cz - 0.2, cx + 0.2, cy + 0.2, cz + 0.2, colorCenter);

                GizmoRenderer.drawQuad(matrices, tessellator, cx, cy, cz, cx + 0.7, cy + 0.7, cz, colorXY);
                GizmoRenderer.drawQuad(matrices, tessellator, cx, cy, cz, cx, cy + 0.7, cz + 0.7, colorYZ);
                GizmoRenderer.drawQuad(matrices, tessellator, cx, cy, cz, cx + 0.7, cy, cz + 0.7, colorZX);

                GizmoRenderer.drawCylinder(matrices, tessellator, cx, cy, cz, 2.0, 0.05, 8, colorX, 1, 0, 0);
                GizmoRenderer.drawCone(matrices, tessellator, cx + 2.0, cy, cz, 0.5, 0.15, 8, colorX, 1, 0, 0);

                GizmoRenderer.drawCylinder(matrices, tessellator, cx, cy, cz, 2.0, 0.05, 8, colorY, 0, 1, 0);
                GizmoRenderer.drawCone(matrices, tessellator, cx, cy + 2.0, cz, 0.5, 0.15, 8, colorY, 0, 1, 0);

                GizmoRenderer.drawCylinder(matrices, tessellator, cx, cy, cz, 2.0, 0.05, 8, colorZ, 0, 0, 1);
                GizmoRenderer.drawCone(matrices, tessellator, cx, cy, cz + 2.0, 0.5, 0.15, 8, colorZ, 0, 0, 1);

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

            if (state.pastePreviewActive && !state.clipboard.isEmpty()) {
                String gameId = state.selectedGameId == null || state.selectedGameId.isBlank()
                        ? state.clipboard.get(0).data().gameId()
                        : state.selectedGameId;
                SessionSnapshotData.EditorExtension previewExtension = SessionSnapshotData.editorExtensions().stream()
                        .filter(ext -> ext.gameId().equalsIgnoreCase(gameId))
                        .findFirst()
                        .orElse(null);
                if (previewExtension != null) {
                    for (SessionSnapshotData.EditorMarker marker : transformedClipboardMarkers(state)) {
                        SessionSnapshotData.EditorMarkerDefinition def = previewExtension.markers().stream()
                                .filter(candidate -> candidate.key().equalsIgnoreCase(marker.definitionKey()))
                                .findFirst()
                                .orElse(null);
                        if (def == null) continue;

                        double px, py, pz;
                        if ("REGION".equalsIgnoreCase(marker.type()) && marker.regions() != null && !marker.regions().isEmpty()) {
                            SessionSnapshotData.EditorRegionPart part = marker.regions().get(0);
                            px = (Math.floor(part.min().x()) + Math.floor(part.max().x()) + 1.0) / 2.0;
                            py = Math.floor(Math.max(part.min().y(), part.max().y())) + 1.0;
                            pz = (Math.floor(part.min().z()) + Math.floor(part.max().z()) + 1.0) / 2.0;
                        } else if (marker.points() != null && !marker.points().isEmpty()) {
                            px = Math.floor(marker.points().get(0).x()) + 0.5;
                            py = Math.floor(marker.points().get(0).y());
                            pz = Math.floor(marker.points().get(0).z()) + 0.5;
                        } else continue;

                        double wx = px - camPos.x;
                        double wy = py - camPos.y;
                        double wz = pz - camPos.z;
                        double worldDist = Math.sqrt(wx * wx + wy * wy + wz * wz);

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
                        if (distSq < 400) {
                            hovered.add(new HoveredMarker(marker, def, previewExtension, worldDist));
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

    private static List<SessionSnapshotData.EditorMarker> transformedClipboardMarkers(MapEditorState state) {
        java.util.List<SessionSnapshotData.EditorMarker> markers = new java.util.ArrayList<>();
        for (MapEditorState.ClipboardMarkerData entry : state.clipboard) {
            SessionSnapshotData.EditorMarker marker = entry.data().marker();
            java.util.List<SessionSnapshotData.EditorPoint> points = new java.util.ArrayList<>();
            for (SessionSnapshotData.EditorPoint point : marker.points()) {
                points.add(transformPoint(state, point));
            }

            java.util.List<SessionSnapshotData.EditorRegionPart> regions = new java.util.ArrayList<>();
            for (SessionSnapshotData.EditorRegionPart region : marker.regions()) {
                regions.add(transformRegion(state, region));
            }

            markers.add(new SessionSnapshotData.EditorMarker(
                    "preview_" + marker.id(),
                    marker.definitionKey(),
                    marker.name(),
                    marker.type(),
                    points,
                    regions,
                    marker.properties()
            ));
        }
        return markers;
    }

    private static SessionSnapshotData.EditorRegionPart transformedClipboardBounds(MapEditorState state) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        boolean any = false;

        for (SessionSnapshotData.EditorMarker marker : transformedClipboardMarkers(state)) {
            for (SessionSnapshotData.EditorPoint point : marker.points()) {
                minX = Math.min(minX, point.x() - 0.5); minY = Math.min(minY, point.y() - 0.5); minZ = Math.min(minZ, point.z() - 0.5);
                maxX = Math.max(maxX, point.x() + 0.5); maxY = Math.max(maxY, point.y() + 0.5); maxZ = Math.max(maxZ, point.z() + 0.5);
                any = true;
            }
            for (SessionSnapshotData.EditorRegionPart region : marker.regions()) {
                minX = Math.min(minX, Math.min(region.min().x(), region.max().x()));
                minY = Math.min(minY, Math.min(region.min().y(), region.max().y()));
                minZ = Math.min(minZ, Math.min(region.min().z(), region.max().z()));
                maxX = Math.max(maxX, Math.max(region.min().x(), region.max().x()));
                maxY = Math.max(maxY, Math.max(region.min().y(), region.max().y()));
                maxZ = Math.max(maxZ, Math.max(region.min().z(), region.max().z()));
                any = true;
            }
        }

        if (!any) return null;
        return new SessionSnapshotData.EditorRegionPart(
                new SessionSnapshotData.EditorPoint(minX, minY, minZ, 0, 0),
                new SessionSnapshotData.EditorPoint(maxX, maxY, maxZ, 0, 0)
        );
    }

    private static SessionSnapshotData.EditorPoint transformPoint(MapEditorState state, SessionSnapshotData.EditorPoint point) {
        double cx = state.selectionCenterX;
        double cy = state.selectionCenterY;
        double cz = state.selectionCenterZ;
        double px = point.x() - cx;
        double py = point.y() - cy;
        double pz = point.z() - cz;
        double radY = Math.toRadians(state.rotY);
        double rx = px * Math.cos(radY) + pz * Math.sin(radY);
        double rz = -px * Math.sin(radY) + pz * Math.cos(radY);
        return new SessionSnapshotData.EditorPoint(
                rx + cx + state.transX,
                py + cy + state.transY,
                rz + cz + state.transZ,
                point.yaw() + (float) state.rotY,
                point.pitch()
        );
    }

    private static SessionSnapshotData.EditorRegionPart transformRegion(MapEditorState state, SessionSnapshotData.EditorRegionPart region) {
        double minX = Math.min(region.min().x(), region.max().x());
        double minY = Math.min(region.min().y(), region.max().y());
        double minZ = Math.min(region.min().z(), region.max().z());
        double maxX = Math.max(region.min().x(), region.max().x());
        double maxY = Math.max(region.min().y(), region.max().y());
        double maxZ = Math.max(region.min().z(), region.max().z());
        double[][] corners = {
                {minX, minY, minZ}, {minX, minY, maxZ},
                {minX, maxY, minZ}, {minX, maxY, maxZ},
                {maxX, minY, minZ}, {maxX, minY, maxZ},
                {maxX, maxY, minZ}, {maxX, maxY, maxZ}
        };

        double outMinX = Double.MAX_VALUE, outMinY = Double.MAX_VALUE, outMinZ = Double.MAX_VALUE;
        double outMaxX = -Double.MAX_VALUE, outMaxY = -Double.MAX_VALUE, outMaxZ = -Double.MAX_VALUE;
        for (double[] corner : corners) {
            SessionSnapshotData.EditorPoint point = transformPoint(state, new SessionSnapshotData.EditorPoint(corner[0], corner[1], corner[2], 0, 0));
            outMinX = Math.min(outMinX, point.x()); outMinY = Math.min(outMinY, point.y()); outMinZ = Math.min(outMinZ, point.z());
            outMaxX = Math.max(outMaxX, point.x()); outMaxY = Math.max(outMaxY, point.y()); outMaxZ = Math.max(outMaxZ, point.z());
        }
        return new SessionSnapshotData.EditorRegionPart(
                new SessionSnapshotData.EditorPoint(outMinX, outMinY, outMinZ, region.min().yaw(), region.min().pitch()),
                new SessionSnapshotData.EditorPoint(outMaxX, outMaxY, outMaxZ, region.max().yaw(), region.max().pitch())
        );
    }

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
