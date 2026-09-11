package dev.frost.miniverse.minigame.impl.pillarsoffortune;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;
import dev.frost.miniverse.map.editor.MapMarker;

import java.util.List;
import java.util.Map;

public record PillarsOfFortuneMapConfig(
    List<MapPosition> spawnPoints
) {
    public static PillarsOfFortuneMapConfig load(String mapId, String preSerializedJson) {
        if (preSerializedJson != null && !preSerializedJson.isBlank()) {
            try {
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(preSerializedJson).getAsJsonObject();
                return fromGamemodeJson(root);
            } catch (Exception e) {
                dev.frost.miniverse.Miniverse.LOGGER.warn("PillarsOfFortuneMapConfig: failed to parse pre-serialized mapConfig, falling back to disk load", e);
            }
        }
        if (mapId == null || mapId.isBlank()) {
            return new PillarsOfFortuneMapConfig(List.of());
        }

        MapEditorExtension extension = MapEditorExtensionRegistry.get(PillarsOfFortuneDefinition.ID).orElse(null);
        if (extension == null) {
            return new PillarsOfFortuneMapConfig(List.of());
        }
        
        Map<String, List<MapMarker>> markers = MapEditorMarkerStore.loadAll(mapId, extension);
        return fromMarkers(markers);
    }

    public static PillarsOfFortuneMapConfig fromGamemodeJson(com.google.gson.JsonObject config) {
        MapEditorExtension extension = MapEditorExtensionRegistry.get(PillarsOfFortuneDefinition.ID).orElse(null);
        if (extension == null) {
            return new PillarsOfFortuneMapConfig(List.of());
        }
        Map<String, List<MapMarker>> markers = MapEditorMarkerStore.loadAll(config, extension);
        return fromMarkers(markers);
    }

    private static PillarsOfFortuneMapConfig fromMarkers(Map<String, List<MapMarker>> markers) {
        List<MapPosition> spawns = markers.getOrDefault("spawn_point", List.of())
            .stream().flatMap(m -> m.points().stream()).toList();
        return new PillarsOfFortuneMapConfig(spawns);
    }
}
