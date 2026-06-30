package dev.frost.miniverse.client.gui.map;

import java.util.HashSet;
import java.util.Set;

public class MapEditorState {
    public static final MapEditorState INSTANCE = new MapEditorState();

    public String selectedGameId = "";
    public String selectedDefinitionKey = "";
    /** Whether the user is currently in the map editor mode (on a map editor server). */
    public boolean editorActive = false;
    /** Per-definition overlay visibility toggles. Contains "gameId:definitionKey" entries that are explicitly enabled. */
    public final Set<String> enabledOverlays = new HashSet<>();
    /** Per-marker overlay visibility toggles. Contains marker IDs that are explicitly hidden. */
    public final Set<String> hiddenIndividualMarkers = new HashSet<>();
    /** Which marker definitions are currently expanded in the UI. */
    public final Set<String> expandedMarkers = new HashSet<>();
    public final java.util.List<dev.frost.miniverse.client.gui.SessionSnapshotData.EditorRegionPart> currentBuilderSelection = new java.util.ArrayList<>();

    public record SelectedMarkerData(dev.frost.miniverse.client.gui.SessionSnapshotData.EditorMarker marker, String gameId, String definitionKey) {}
    public record ClipboardMarkerData(SelectedMarkerData data, double relX, double relY, double relZ, int copyYawSteps) {}
    
    public final java.util.List<ClipboardMarkerData> clipboard = new java.util.ArrayList<>();
    public final java.util.List<dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint> placementPoints = new java.util.ArrayList<>();

    // Gizmo state
    public int hoveredAxis = 0; // 0=None, 1=X, 2=Y, 3=Z
    public int gizmoMode = 0; // 0=Translate, 1=Rotate, 2=Scale
    public double transX = 0, transY = 0, transZ = 0;
    public double scaleX = 1, scaleY = 1, scaleZ = 1;
    public double rotY = 0; // in degrees
    
    public double selectionCenterX = 0, selectionCenterY = 0, selectionCenterZ = 0;
    
    public String selectedTeam = "";

    public void clear() {
        this.selectedGameId = "";
        this.selectedDefinitionKey = "";
        this.editorActive = false;
        this.enabledOverlays.clear();
        this.hiddenIndividualMarkers.clear();
        this.expandedMarkers.clear();
        this.currentBuilderSelection.clear();
        this.placementPoints.clear();
        this.clipboard.clear();
        this.hoveredAxis = 0;
        this.transX = 0; this.transY = 0; this.transZ = 0;
        this.scaleX = 1; this.scaleY = 1; this.scaleZ = 1;
        this.rotY = 0;
    }

    public boolean isOverlayEnabled(String gameId, String definitionKey) {
        return this.enabledOverlays.contains(overlayKey(gameId, definitionKey));
    }

    public void toggleOverlay(String gameId, String definitionKey) {
        String key = overlayKey(gameId, definitionKey);
        if (!this.enabledOverlays.remove(key)) {
            this.enabledOverlays.add(key);
        }
    }

    public void enableOverlay(String gameId, String definitionKey) {
        this.enabledOverlays.add(overlayKey(gameId, definitionKey));
    }

    public void disableOverlay(String gameId, String definitionKey) {
        this.enabledOverlays.remove(overlayKey(gameId, definitionKey));
    }

    private static String overlayKey(String gameId, String definitionKey) {
        return gameId.toLowerCase() + ":" + definitionKey.toLowerCase();
    }
}
