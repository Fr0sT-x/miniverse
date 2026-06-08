package dev.frost.miniverse.client.gui.map;

import java.util.HashSet;
import java.util.Set;

public class MapEditorState {
    public static final MapEditorState INSTANCE = new MapEditorState();

    public String selectedGameId = "";
    public String selectedDefinitionKey = "";
    /** Whether the user is currently in the map editor mode (on a map editor server). */
    public boolean editorActive = false;
    /** Per-definition overlay visibility toggles. Contains "gameId:definitionKey" entries that are explicitly disabled. */
    public final Set<String> disabledOverlays = new HashSet<>();
    /** Per-marker overlay visibility toggles. Contains marker IDs that are explicitly hidden. */
    public final Set<String> hiddenIndividualMarkers = new HashSet<>();
    /** Which marker definitions are currently expanded in the UI. */
    public final Set<String> expandedMarkers = new HashSet<>();
    /** The currently unsaved region parts being built by the user. */
    public final java.util.List<dev.frost.miniverse.client.gui.SessionSnapshotData.EditorRegionPart> currentBuilderSelection = new java.util.ArrayList<>();

    public void clear() {
        this.selectedGameId = "";
        this.selectedDefinitionKey = "";
        this.editorActive = false;
        this.disabledOverlays.clear();
        this.hiddenIndividualMarkers.clear();
        this.expandedMarkers.clear();
        this.currentBuilderSelection.clear();
    }

    public boolean isOverlayEnabled(String gameId, String definitionKey) {
        return !this.disabledOverlays.contains(overlayKey(gameId, definitionKey));
    }

    public void toggleOverlay(String gameId, String definitionKey) {
        String key = overlayKey(gameId, definitionKey);
        if (!this.disabledOverlays.remove(key)) {
            this.disabledOverlays.add(key);
        }
    }

    public void enableOverlay(String gameId, String definitionKey) {
        this.disabledOverlays.remove(overlayKey(gameId, definitionKey));
    }

    public void disableOverlay(String gameId, String definitionKey) {
        this.disabledOverlays.add(overlayKey(gameId, definitionKey));
    }

    private static String overlayKey(String gameId, String definitionKey) {
        return gameId.toLowerCase() + ":" + definitionKey.toLowerCase();
    }
}
