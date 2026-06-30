package dev.frost.miniverse.client.gui.map;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class MapEditorWorkspaceScreen extends Screen {

    public static MapEditorWorkspaceScreen INSTANCE;

    public MapEditorWorkspaceScreen() {
        super(Text.literal("Map Editor Workspace"));
        INSTANCE = this;
    }
    
    private LeftToolPalette leftSidebar;
    private RightInspector rightSidebar;
    private String activeTool = "SELECT";

    @Override
    protected void init() {
        super.init();
        
        int sidebarWidth = 160;
        
        // Left Tool Palette
        leftSidebar = new LeftToolPalette(0, 0, sidebarWidth, this.height);
        this.addDrawableChild(leftSidebar);
        
        // Right Inspector
        rightSidebar = new RightInspector(this.width - sidebarWidth, 0, sidebarWidth, this.height);
        this.addDrawableChild(rightSidebar);
    }

    public void setActiveTool(String tool) {
        this.activeTool = tool;
        // TODO: Update visual state
    }

    public void confirmPaste() {
        commitTransform();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        net.minecraft.client.option.KeyBinding.setKeyPressed(net.minecraft.client.util.InputUtil.fromKeyCode(keyCode, scanCode), true);
        net.minecraft.client.option.KeyBinding.onKeyPressed(net.minecraft.client.util.InputUtil.fromKeyCode(keyCode, scanCode));
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (super.keyReleased(keyCode, scanCode, modifiers)) return true;
        net.minecraft.client.option.KeyBinding.setKeyPressed(net.minecraft.client.util.InputUtil.fromKeyCode(keyCode, scanCode), false);
        return true;
    }

    public MapEditorState.SelectedMarkerData selectedMarker = null;

    private double selectionStartX = -1;
    private double selectionStartY = -1;
    private double selectionCurrentX = -1;
    private double selectionCurrentY = -1;
    private boolean isSelecting = false;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true; // Clicked on UI
        }
        
        MapEditorState state = MapEditorState.INSTANCE;
        if (button == 0 && client != null && client.player != null) { // Left click in 3D world
            if (!state.clipboard.isEmpty() && state.hoveredTarget != MapEditorState.GizmoTarget.NONE) {
                state.clickedTarget = state.hoveredTarget;
                state.initialTransX = state.transX;
                state.initialTransY = state.transY;
                state.initialTransZ = state.transZ;
                state.initialRotY = state.rotY;
                state.initialScaleX = state.scaleX;
                state.initialScaleY = state.scaleY;
                state.initialScaleZ = state.scaleZ;
                
                org.joml.Vector3d rayDir = GizmoMath.unprojectMouseToRay(client, mouseX, mouseY);
                if (rayDir != null) {
                    net.minecraft.util.math.Vec3d origin = client.player.getCameraPosVec(1.0f);
                    org.joml.Vector3d rayOrigin = new org.joml.Vector3d(origin.x, origin.y, origin.z);
                    double cx = state.selectionCenterX + state.transX;
                    double cy = state.selectionCenterY + state.transY;
                    double cz = state.selectionCenterZ + state.transZ;
                    org.joml.Vector3d center = new org.joml.Vector3d(cx, cy, cz);
                    
                    switch (state.clickedTarget) {
                        case TRANSLATE_X: case SCALE_X:
                            state.dragStartIntersection = GizmoMath.getClosestPointOnAxis(rayOrigin, rayDir, center, new org.joml.Vector3d(1, 0, 0));
                            break;
                        case TRANSLATE_Y: case SCALE_Y:
                            state.dragStartIntersection = GizmoMath.getClosestPointOnAxis(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 1, 0));
                            break;
                        case TRANSLATE_Z: case SCALE_Z:
                            state.dragStartIntersection = GizmoMath.getClosestPointOnAxis(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 0, 1));
                            break;
                        case TRANSLATE_XY:
                            state.dragStartIntersection = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 0, 1));
                            break;
                        case TRANSLATE_YZ:
                            state.dragStartIntersection = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(1, 0, 0));
                            break;
                        case TRANSLATE_ZX: case ROTATE_Y:
                            state.dragStartIntersection = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 1, 0));
                            break;
                        case TRANSLATE_XYZ: case SCALE_XYZ:
                            net.minecraft.util.math.Vec3d look = client.player.getRotationVec(1.0f);
                            state.dragStartIntersection = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(-look.x, -look.y, -look.z).normalize());
                            break;
                        default:
                            break;
                    }
                }
                return true;
            }

            if ("SELECT".equals(activeTool)) {
                isSelecting = true;
                selectionStartX = mouseX;
                selectionStartY = mouseY;
                selectionCurrentX = mouseX;
                selectionCurrentY = mouseY;
                return true;
            } else {
                // Raycast fallback for single click logic if needed
                org.joml.Vector3d rayDirJoml = GizmoMath.unprojectMouseToRay(client, mouseX, mouseY);
                if (rayDirJoml != null) {
                    net.minecraft.util.math.Vec3d rayOrigin = client.player.getCameraPosVec(1.0f);
                    net.minecraft.util.math.Vec3d rayEnd = rayOrigin.add(rayDirJoml.x * 1000, rayDirJoml.y * 1000, rayDirJoml.z * 1000);
    
                    MapEditorState.SelectedMarkerData closestMarker = null;
                    double closestDist = Double.MAX_VALUE;
    
                    for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorExtension ext : dev.frost.miniverse.client.gui.SessionSnapshotData.editorExtensions()) {
                        for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorMarkerDefinition def : ext.markers()) {
                            if (!MapEditorState.INSTANCE.isOverlayEnabled(ext.gameId(), def.key())) continue;
    
                            java.util.List<dev.frost.miniverse.client.gui.SessionSnapshotData.EditorMarker> markers = dev.frost.miniverse.client.gui.SessionSnapshotData.editorState().markers(ext.gameId(), def.key());
                            if (markers == null) continue;
    
                            for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorMarker marker : markers) {
                                if (MapEditorState.INSTANCE.hiddenIndividualMarkers.contains(marker.id())) continue;
    
                                if ("REGION".equalsIgnoreCase(marker.type())) {
                                    for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorRegionPart region : marker.regions()) {
                                        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(region.min().x(), region.min().y(), region.min().z(), region.max().x(), region.max().y(), region.max().z());
                                        java.util.Optional<net.minecraft.util.math.Vec3d> hit = box.raycast(rayOrigin, rayEnd);
                                        if (hit.isPresent()) {
                                            double dist = hit.get().squaredDistanceTo(rayOrigin);
                                            if (dist < closestDist) {
                                                closestDist = dist;
                                                closestMarker = new MapEditorState.SelectedMarkerData(marker, ext.gameId(), def.key());
                                            }
                                        }
                                    }
                                } else if ("POINT".equalsIgnoreCase(marker.type()) && !marker.points().isEmpty()) {
                                    for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint pt : marker.points()) {
                                        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(pt.x() - 0.5, pt.y() - 0.5, pt.z() - 0.5, pt.x() + 0.5, pt.y() + 0.5, pt.z() + 0.5);
                                        java.util.Optional<net.minecraft.util.math.Vec3d> hit = box.raycast(rayOrigin, rayEnd);
                                        if (hit.isPresent()) {
                                            double dist = hit.get().squaredDistanceTo(rayOrigin);
                                            if (dist < closestDist) {
                                                closestDist = dist;
                                                closestMarker = new MapEditorState.SelectedMarkerData(marker, ext.gameId(), def.key());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
    
                    this.selectedMarker = closestMarker;
                    if (closestMarker != null) {
                        client.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("Selected marker: " + closestMarker.marker().id()));
                    }
                }
                return true;
            }
        }
        return false;
    }

    public void rotatePaste() {
        MapEditorState.INSTANCE.rotY += 90.0;
    }

    public void commitTransform() {
        if (this.client == null) return;
        MapEditorState state = MapEditorState.INSTANCE;
        if (state.clipboard.isEmpty()) return;

        double cx = state.selectionCenterX;
        double cy = state.selectionCenterY;
        double cz = state.selectionCenterZ;
        
        double tx = state.transX;
        double ty = state.transY;
        double tz = state.transZ;
        double radY = Math.toRadians(state.rotY);
        double sx = state.scaleX;
        double sy = state.scaleY;
        double sz = state.scaleZ;

        boolean isPaste = "PASTE".equals(this.activeTool);
        
        com.google.gson.JsonArray bulkMarkers = new com.google.gson.JsonArray();
        
        for (MapEditorState.ClipboardMarkerData cmd : state.clipboard) {
            dev.frost.miniverse.client.gui.SessionSnapshotData.EditorMarker m = cmd.data().marker();
            
            com.google.gson.JsonArray newPoints = new com.google.gson.JsonArray();
            for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint p : m.points()) {
                // translate to origin
                double px = p.x() - cx;
                double py = p.y() - cy;
                double pz = p.z() - cz;
                
                // scale
                px *= sx; py *= sy; pz *= sz;
                
                // rotate Y
                double rx = px * Math.cos(radY) + pz * Math.sin(radY);
                double rz = -px * Math.sin(radY) + pz * Math.cos(radY);
                
                // translate back and apply offset
                double nx = rx + cx + tx;
                double ny = py + cy + ty;
                double nz = rz + cz + tz;
                
                com.google.gson.JsonObject po = new com.google.gson.JsonObject();
                po.addProperty("x", nx); po.addProperty("y", ny); po.addProperty("z", nz);
                po.addProperty("yaw", p.yaw() + (float)state.rotY); po.addProperty("pitch", p.pitch());
                newPoints.add(po);
            }
            
            com.google.gson.JsonArray newRegions = new com.google.gson.JsonArray();
            for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorRegionPart r : m.regions()) {
                double minx = r.min().x() - cx, miny = r.min().y() - cy, minz = r.min().z() - cz;
                minx *= sx; miny *= sy; minz *= sz;
                double minrx = minx * Math.cos(radY) + minz * Math.sin(radY);
                double minrz = -minx * Math.sin(radY) + minz * Math.cos(radY);
                double fminx = minrx + cx + tx, fminy = miny + cy + ty, fminz = minrz + cz + tz;
                
                double maxx = r.max().x() - cx, maxy = r.max().y() - cy, maxz = r.max().z() - cz;
                maxx *= sx; maxy *= sy; maxz *= sz;
                double maxrx = maxx * Math.cos(radY) + maxz * Math.sin(radY);
                double maxrz = -maxx * Math.sin(radY) + maxz * Math.cos(radY);
                double fmaxx = maxrx + cx + tx, fmaxy = maxy + cy + ty, fmaxz = maxrz + cz + tz;
                
                com.google.gson.JsonObject rmin = new com.google.gson.JsonObject();
                rmin.addProperty("x", Math.min(fminx, fmaxx)); rmin.addProperty("y", Math.min(fminy, fmaxy)); rmin.addProperty("z", Math.min(fminz, fmaxz));
                rmin.addProperty("yaw", r.min().yaw()); rmin.addProperty("pitch", r.min().pitch());
                
                com.google.gson.JsonObject rmax = new com.google.gson.JsonObject();
                rmax.addProperty("x", Math.max(fminx, fmaxx)); rmax.addProperty("y", Math.max(fminy, fmaxy)); rmax.addProperty("z", Math.max(fminz, fmaxz));
                rmax.addProperty("yaw", r.max().yaw()); rmax.addProperty("pitch", r.max().pitch());
                
                com.google.gson.JsonObject ro = new com.google.gson.JsonObject();
                ro.add("min", rmin);
                ro.add("max", rmax);
                newRegions.add(ro);
            }
            
            if (isPaste) {
                com.google.gson.JsonObject mo = new com.google.gson.JsonObject();
                mo.addProperty("definitionKey", m.definitionKey());
                mo.addProperty("name", m.name());
                com.google.gson.JsonObject newProps = m.properties() != null ? m.properties().deepCopy() : new com.google.gson.JsonObject();
                if (!state.selectedTeam.isEmpty()) {
                    newProps.addProperty("teamId", state.selectedTeam);
                }
                mo.add("properties", newProps);
                mo.add("points", newPoints);
                mo.add("regions", newRegions);
                bulkMarkers.add(mo);
            } else { // MOVE
                net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
                nbt.putString("action", "move_marker");
                nbt.putString("gameId", cmd.data().gameId());
                nbt.putString("definitionKey", m.definitionKey());
                nbt.putString("markerId", m.id());
                nbt.putString("points", newPoints.toString());
                nbt.putString("regions", newRegions.toString());
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.frost.miniverse.common.NetworkConstants.MapEditorActionPayload(nbt));
            }
        }
        
        if (isPaste) {
            net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
            nbt.putString("action", "add_spatial_bulk");
            nbt.putString("gameId", state.selectedGameId); // Assumption: paste into same game mode
            nbt.putString("markers", bulkMarkers.toString());
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.frost.miniverse.common.NetworkConstants.MapEditorActionPayload(nbt));
            client.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("Pasted " + bulkMarkers.size() + " markers."));
        } else {
            client.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("Moved " + state.clipboard.size() + " markers."));
            // Keep clipboard for subsequent moves, but update selection center and reset transforms
            state.selectionCenterX += tx;
            state.selectionCenterY += ty;
            state.selectionCenterZ += tz;
            state.transX = 0; state.transY = 0; state.transZ = 0;
            state.rotY = 0;
            state.scaleX = 1; state.scaleY = 1; state.scaleZ = 1;
        }
    }
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isSelecting) {
            isSelecting = false;
            
            double minX = Math.min(selectionStartX, selectionCurrentX);
            double maxX = Math.max(selectionStartX, selectionCurrentX);
            double minY = Math.min(selectionStartY, selectionCurrentY);
            double maxY = Math.max(selectionStartY, selectionCurrentY);

            if (Math.abs(maxX - minX) > 5 || Math.abs(maxY - minY) > 5) {
                java.util.List<MapEditorState.SelectedMarkerData> selected = new java.util.ArrayList<>();

                for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorExtension ext : dev.frost.miniverse.client.gui.SessionSnapshotData.editorExtensions()) {
                    for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorMarkerDefinition def : ext.markers()) {
                        if (!MapEditorState.INSTANCE.isOverlayEnabled(ext.gameId(), def.key())) continue;
                        java.util.List<dev.frost.miniverse.client.gui.SessionSnapshotData.EditorMarker> markers = dev.frost.miniverse.client.gui.SessionSnapshotData.editorState().markers(ext.gameId(), def.key());
                        if (markers == null) continue;
                        for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorMarker marker : markers) {
                            if (MapEditorState.INSTANCE.hiddenIndividualMarkers.contains(marker.id())) continue;

                            boolean inside = false;
                            if ("REGION".equalsIgnoreCase(marker.type())) {
                                for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorRegionPart region : marker.regions()) {
                                    double[] xs = {region.min().x(), region.max().x()};
                                    double[] ys = {region.min().y(), region.max().y()};
                                    double[] zs = {region.min().z(), region.max().z()};
                                    for (double x : xs) {
                                        for (double y : ys) {
                                            for (double z : zs) {
                                                org.joml.Vector2d proj = GizmoMath.project3DTo2D(client, x, y, z);
                                                if (proj != null && proj.x >= minX && proj.x <= maxX && proj.y >= minY && proj.y <= maxY) {
                                                    inside = true; break;
                                                }
                                            }
                                            if (inside) break;
                                        }
                                        if (inside) break;
                                    }
                                    if (inside) break;
                                }
                            } else if ("POINT".equalsIgnoreCase(marker.type())) {
                                for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint pt : marker.points()) {
                                    org.joml.Vector2d proj = GizmoMath.project3DTo2D(client, pt.x(), pt.y(), pt.z());
                                    if (proj != null && proj.x >= minX && proj.x <= maxX && proj.y >= minY && proj.y <= maxY) {
                                        inside = true; break;
                                    }
                                }
                            }
                            if (inside) {
                                selected.add(new MapEditorState.SelectedMarkerData(marker, ext.gameId(), def.key()));
                            }
                        }
                    }
                }

                if (!selected.isEmpty()) {
                    double boxMinX = Double.MAX_VALUE, boxMinY = Double.MAX_VALUE, boxMinZ = Double.MAX_VALUE;
                    double boxMaxX = -Double.MAX_VALUE, boxMaxY = -Double.MAX_VALUE, boxMaxZ = -Double.MAX_VALUE;
                    for (MapEditorState.SelectedMarkerData smd : selected) {
                        dev.frost.miniverse.client.gui.SessionSnapshotData.EditorMarker marker = smd.marker();
                        if ("REGION".equalsIgnoreCase(marker.type())) {
                            for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorRegionPart r : marker.regions()) {
                                boxMinX = Math.min(boxMinX, r.min().x()); boxMinY = Math.min(boxMinY, r.min().y()); boxMinZ = Math.min(boxMinZ, r.min().z());
                                boxMaxX = Math.max(boxMaxX, r.max().x()); boxMaxY = Math.max(boxMaxY, r.max().y()); boxMaxZ = Math.max(boxMaxZ, r.max().z());
                            }
                        } else if ("POINT".equalsIgnoreCase(marker.type())) {
                            for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint p : marker.points()) {
                                boxMinX = Math.min(boxMinX, p.x() - 0.5); boxMinY = Math.min(boxMinY, p.y() - 0.5); boxMinZ = Math.min(boxMinZ, p.z() - 0.5);
                                boxMaxX = Math.max(boxMaxX, p.x() + 0.5); boxMaxY = Math.max(boxMaxY, p.y() + 0.5); boxMaxZ = Math.max(boxMaxZ, p.z() + 0.5);
                            }
                        }
                    }

                    dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint pMin = new dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint(boxMinX, boxMinY, boxMinZ, 0f, 0f);
                    dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint pMax = new dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint(boxMaxX, boxMaxY, boxMaxZ, 0f, 0f);
                    MapEditorState.INSTANCE.currentBuilderSelection.clear();
                    MapEditorState.INSTANCE.currentBuilderSelection.add(new dev.frost.miniverse.client.gui.SessionSnapshotData.EditorRegionPart(pMin, pMax));

                    double centerX = (boxMinX + boxMaxX) / 2.0;
                    double centerY = (boxMinY + boxMaxY) / 2.0;
                    double centerZ = (boxMinZ + boxMaxZ) / 2.0;
                    
                    MapEditorState.INSTANCE.selectionCenterX = centerX;
                    MapEditorState.INSTANCE.selectionCenterY = centerY;
                    MapEditorState.INSTANCE.selectionCenterZ = centerZ;
                    MapEditorState.INSTANCE.transX = 0;
                    MapEditorState.INSTANCE.transY = 0;
                    MapEditorState.INSTANCE.transZ = 0;
                    MapEditorState.INSTANCE.scaleX = 1;
                    MapEditorState.INSTANCE.scaleY = 1;
                    MapEditorState.INSTANCE.scaleZ = 1;
                    MapEditorState.INSTANCE.rotY = 0;

                    MapEditorState.INSTANCE.clipboard.clear();
                    for (MapEditorState.SelectedMarkerData smd : selected) {
                        double relX = 0, relY = 0, relZ = 0;
                        if ("REGION".equalsIgnoreCase(smd.marker().type()) && !smd.marker().regions().isEmpty()) {
                            dev.frost.miniverse.client.gui.SessionSnapshotData.EditorRegionPart r = smd.marker().regions().get(0);
                            relX = ((r.min().x() + r.max().x()) / 2.0) - centerX;
                            relY = ((r.min().y() + r.max().y()) / 2.0) - centerY;
                            relZ = ((r.min().z() + r.max().z()) / 2.0) - centerZ;
                        } else if ("POINT".equalsIgnoreCase(smd.marker().type()) && !smd.marker().points().isEmpty()) {
                            dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint p = smd.marker().points().get(0);
                            relX = p.x() - centerX;
                            relY = p.y() - centerY;
                            relZ = p.z() - centerZ;
                        }
                        MapEditorState.INSTANCE.clipboard.add(new MapEditorState.ClipboardMarkerData(smd, relX, relY, relZ, 0));
                    }
                    client.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("Selected " + selected.size() + " markers."));
                } else {
                    MapEditorState.INSTANCE.currentBuilderSelection.clear();
                    MapEditorState.INSTANCE.clipboard.clear();
                }
                
                if (rightSidebar != null) {
                    rightSidebar.update();
                }
            }
            return true;
        }
        if (button == 0) {
            MapEditorState.INSTANCE.clickedTarget = MapEditorState.GizmoTarget.NONE;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        MapEditorState state = MapEditorState.INSTANCE;
        if (button == 0 && state.clickedTarget != MapEditorState.GizmoTarget.NONE && state.dragStartIntersection != null && client != null && client.player != null) {
            org.joml.Vector3d rayDir = GizmoMath.unprojectMouseToRay(client, mouseX, mouseY);
            if (rayDir != null) {
                net.minecraft.util.math.Vec3d origin = client.player.getCameraPosVec(1.0f);
                org.joml.Vector3d rayOrigin = new org.joml.Vector3d(origin.x, origin.y, origin.z);
                
                double cx = state.selectionCenterX + state.initialTransX;
                double cy = state.selectionCenterY + state.initialTransY;
                double cz = state.selectionCenterZ + state.initialTransZ;
                org.joml.Vector3d center = new org.joml.Vector3d(cx, cy, cz);
                
                switch (state.clickedTarget) {
                    case TRANSLATE_X: {
                        org.joml.Vector3d currentHit = GizmoMath.getClosestPointOnAxis(rayOrigin, rayDir, center, new org.joml.Vector3d(1, 0, 0));
                        state.transX = state.initialTransX + (currentHit.x - state.dragStartIntersection.x);
                        break;
                    }
                    case TRANSLATE_Y: {
                        org.joml.Vector3d currentHit = GizmoMath.getClosestPointOnAxis(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 1, 0));
                        state.transY = state.initialTransY + (currentHit.y - state.dragStartIntersection.y);
                        break;
                    }
                    case TRANSLATE_Z: {
                        org.joml.Vector3d currentHit = GizmoMath.getClosestPointOnAxis(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 0, 1));
                        state.transZ = state.initialTransZ + (currentHit.z - state.dragStartIntersection.z);
                        break;
                    }
                    case TRANSLATE_XY: {
                        org.joml.Vector3d currentHit = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 0, 1));
                        if (currentHit != null) {
                            state.transX = state.initialTransX + (currentHit.x - state.dragStartIntersection.x);
                            state.transY = state.initialTransY + (currentHit.y - state.dragStartIntersection.y);
                        }
                        break;
                    }
                    case TRANSLATE_YZ: {
                        org.joml.Vector3d currentHit = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(1, 0, 0));
                        if (currentHit != null) {
                            state.transY = state.initialTransY + (currentHit.y - state.dragStartIntersection.y);
                            state.transZ = state.initialTransZ + (currentHit.z - state.dragStartIntersection.z);
                        }
                        break;
                    }
                    case TRANSLATE_ZX: {
                        org.joml.Vector3d currentHit = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 1, 0));
                        if (currentHit != null) {
                            state.transX = state.initialTransX + (currentHit.x - state.dragStartIntersection.x);
                            state.transZ = state.initialTransZ + (currentHit.z - state.dragStartIntersection.z);
                        }
                        break;
                    }
                    case TRANSLATE_XYZ: {
                        net.minecraft.util.math.Vec3d look = client.player.getRotationVec(1.0f);
                        org.joml.Vector3d planeNormal = new org.joml.Vector3d(-look.x, -look.y, -look.z).normalize();
                        org.joml.Vector3d currentHit = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, planeNormal);
                        if (currentHit != null) {
                            state.transX = state.initialTransX + (currentHit.x - state.dragStartIntersection.x);
                            state.transY = state.initialTransY + (currentHit.y - state.dragStartIntersection.y);
                            state.transZ = state.initialTransZ + (currentHit.z - state.dragStartIntersection.z);
                        }
                        break;
                    }
                    case ROTATE_Y: {
                        org.joml.Vector3d currentHit = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 1, 0));
                        if (currentHit != null) {
                            double startAngle = Math.atan2(state.dragStartIntersection.z - cz, state.dragStartIntersection.x - cx);
                            double currentAngle = Math.atan2(currentHit.z - cz, currentHit.x - cx);
                            double angleDiff = Math.toDegrees(currentAngle - startAngle);
                            // Snap to 45 degrees
                            angleDiff = Math.round(angleDiff / 45.0) * 45.0;
                            state.rotY = state.initialRotY + angleDiff;
                        }
                        break;
                    }
                    case SCALE_X: {
                        org.joml.Vector3d currentHit = GizmoMath.getClosestPointOnAxis(rayOrigin, rayDir, center, new org.joml.Vector3d(1, 0, 0));
                        double distStart = state.dragStartIntersection.x - cx;
                        double distCurrent = currentHit.x - cx;
                        if (distStart != 0) state.scaleX = state.initialScaleX * (distCurrent / distStart);
                        break;
                    }
                    case SCALE_Y: {
                        org.joml.Vector3d currentHit = GizmoMath.getClosestPointOnAxis(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 1, 0));
                        double distStart = state.dragStartIntersection.y - cy;
                        double distCurrent = currentHit.y - cy;
                        if (distStart != 0) state.scaleY = state.initialScaleY * (distCurrent / distStart);
                        break;
                    }
                    case SCALE_Z: {
                        org.joml.Vector3d currentHit = GizmoMath.getClosestPointOnAxis(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 0, 1));
                        double distStart = state.dragStartIntersection.z - cz;
                        double distCurrent = currentHit.z - cz;
                        if (distStart != 0) state.scaleZ = state.initialScaleZ * (distCurrent / distStart);
                        break;
                    }
                    case SCALE_XYZ: {
                        net.minecraft.util.math.Vec3d look = client.player.getRotationVec(1.0f);
                        org.joml.Vector3d planeNormal = new org.joml.Vector3d(-look.x, -look.y, -look.z).normalize();
                        org.joml.Vector3d currentHit = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, planeNormal);
                        if (currentHit != null) {
                            double distStart = state.dragStartIntersection.distance(center);
                            double distCurrent = currentHit.distance(center);
                            if (distStart != 0) {
                                double scale = distCurrent / distStart;
                                if (currentHit.y < center.y) scale = 1.0 / scale; // Basic directional scaling
                                state.scaleX = state.initialScaleX * scale;
                                state.scaleY = state.initialScaleY * scale;
                                state.scaleZ = state.initialScaleZ * scale;
                            }
                        }
                        break;
                    }
                }
            }
            return true;
        }

        if (button == 0 && isSelecting) {
            selectionCurrentX = mouseX;
            selectionCurrentY = mouseY;
            return true;
        }
        if (button == 1 && client != null && client.player != null) { // Right click to rotate camera
            client.player.setYaw(client.player.getYaw() + (float) deltaX * 0.15f);
            client.player.setPitch(client.player.getPitch() + (float) deltaY * 0.15f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        // Gizmo Hover Detection
        MapEditorState state = MapEditorState.INSTANCE;
        if (!state.clipboard.isEmpty() && client != null && client.player != null) {
            org.joml.Vector3d rayDir = GizmoMath.unprojectMouseToRay(client, mouseX, mouseY);
            if (rayDir != null) {
                net.minecraft.util.math.Vec3d origin = client.player.getCameraPosVec(1.0f);
                org.joml.Vector3d rayOrigin = new org.joml.Vector3d(origin.x, origin.y, origin.z);
                
                double cx = state.selectionCenterX + state.transX;
                double cy = state.selectionCenterY + state.transY;
                double cz = state.selectionCenterZ + state.transZ;
                org.joml.Vector3d center = new org.joml.Vector3d(cx, cy, cz);

                state.hoveredTarget = MapEditorState.GizmoTarget.NONE;
                double threshold = 0.2;

                if (state.gizmoMode == 0 || state.gizmoMode == 2) {
                    // Check Center
                    double distCenter = GizmoMath.distanceToSegment(rayOrigin, rayDir, center, center);
                    if (distCenter < threshold) {
                        state.hoveredTarget = state.gizmoMode == 0 ? MapEditorState.GizmoTarget.TRANSLATE_XYZ : MapEditorState.GizmoTarget.SCALE_XYZ;
                        return;
                    }

                    // Check Planes (only Translate)
                    if (state.gizmoMode == 0) {
                        org.joml.Vector3d hitXY = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 0, 1));
                        org.joml.Vector3d hitYZ = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(1, 0, 0));
                        org.joml.Vector3d hitZX = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 1, 0));

                        if (hitXY != null && hitXY.x >= cx && hitXY.x <= cx + 0.7 && hitXY.y >= cy && hitXY.y <= cy + 0.7) {
                            state.hoveredTarget = MapEditorState.GizmoTarget.TRANSLATE_XY;
                            return;
                        }
                        if (hitYZ != null && hitYZ.y >= cy && hitYZ.y <= cy + 0.7 && hitYZ.z >= cz && hitYZ.z <= cz + 0.7) {
                            state.hoveredTarget = MapEditorState.GizmoTarget.TRANSLATE_YZ;
                            return;
                        }
                        if (hitZX != null && hitZX.x >= cx && hitZX.x <= cx + 0.7 && hitZX.z >= cz && hitZX.z <= cz + 0.7) {
                            state.hoveredTarget = MapEditorState.GizmoTarget.TRANSLATE_ZX;
                            return;
                        }
                    }

                    // Check Axes
                    double distX = GizmoMath.distanceToSegment(rayOrigin, rayDir, center, new org.joml.Vector3d(cx + 2.5, cy, cz));
                    double distY = GizmoMath.distanceToSegment(rayOrigin, rayDir, center, new org.joml.Vector3d(cx, cy + 2.5, cz));
                    double distZ = GizmoMath.distanceToSegment(rayOrigin, rayDir, center, new org.joml.Vector3d(cx, cy, cz + 2.5));

                    if (distX < threshold && distX < distY && distX < distZ) state.hoveredTarget = state.gizmoMode == 0 ? MapEditorState.GizmoTarget.TRANSLATE_X : MapEditorState.GizmoTarget.SCALE_X;
                    else if (distY < threshold && distY < distX && distY < distZ) state.hoveredTarget = state.gizmoMode == 0 ? MapEditorState.GizmoTarget.TRANSLATE_Y : MapEditorState.GizmoTarget.SCALE_Y;
                    else if (distZ < threshold && distZ < distX && distZ < distY) state.hoveredTarget = state.gizmoMode == 0 ? MapEditorState.GizmoTarget.TRANSLATE_Z : MapEditorState.GizmoTarget.SCALE_Z;
                } else if (state.gizmoMode == 1) { // Rotate
                    // Only Y for now
                    org.joml.Vector3d hitXZ = GizmoMath.intersectRayPlane(rayOrigin, rayDir, center, new org.joml.Vector3d(0, 1, 0));
                    if (hitXZ != null) {
                        double distToCenter = Math.sqrt(Math.pow(hitXZ.x - cx, 2) + Math.pow(hitXZ.z - cz, 2));
                        if (distToCenter >= 1.3 && distToCenter <= 1.7) {
                            state.hoveredTarget = MapEditorState.GizmoTarget.ROTATE_Y;
                        }
                    }
                }
            }
        }
    }



    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        if (isSelecting) {
            int minX = (int) Math.min(selectionStartX, selectionCurrentX);
            int maxX = (int) Math.max(selectionStartX, selectionCurrentX);
            int minY = (int) Math.min(selectionStartY, selectionCurrentY);
            int maxY = (int) Math.max(selectionStartY, selectionCurrentY);

            // Draw translucent blue box with solid border
            context.fill(minX, minY, maxX, maxY, 0x4000AAFF);
            context.fill(minX, minY, maxX, minY + 1, 0xFF00AAFF);
            context.fill(minX, maxY - 1, maxX, maxY, 0xFF00AAFF);
            context.fill(minX, minY, minX + 1, maxY, 0xFF00AAFF);
            context.fill(maxX - 1, minY, maxX, maxY, 0xFF00AAFF);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        super.close();
        if (INSTANCE == this) {
            INSTANCE = null;
        }
    }
}
