package dev.frost.miniverse.client.gui.map;

import java.util.HashMap;
import java.util.Map;

public final class MapEditorCustomRendererRegistry {
    private static final Map<String, MapEditorCustomRenderer> RENDERERS = new HashMap<>();

    public static void register(String markerDefinitionKey, MapEditorCustomRenderer renderer) {
        RENDERERS.put(markerDefinitionKey, renderer);
    }

    public static MapEditorCustomRenderer get(String markerDefinitionKey) {
        return RENDERERS.get(markerDefinitionKey);
    }
}
