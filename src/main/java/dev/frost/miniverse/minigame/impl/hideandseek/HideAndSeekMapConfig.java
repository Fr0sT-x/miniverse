package dev.frost.miniverse.minigame.impl.hideandseek;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;
import dev.frost.miniverse.map.editor.MapMarker;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record HideAndSeekMapConfig(
    List<MapPosition> hiderSpawns,
    List<MapPosition> seekerSpawns,
    Optional<MapPosition> waitingLobby,
    List<String> disguiseBlocks
) {
    public MapValidationResult validate() {
        MapValidationResult.Builder result = MapValidationResult.builder();
        if (this.hiderSpawns.isEmpty()) {
            result.error("No Hider Spawns Configured");
        }
        if (this.seekerSpawns.isEmpty()) {
            result.error("No Seeker Spawns Configured");
        }
        return result.build();
    }

    public static HideAndSeekMapConfig load(String mapId, String preSerializedJson) {
        if (preSerializedJson != null && !preSerializedJson.isBlank()) {
            try {
                JsonObject root = JsonParser.parseString(preSerializedJson).getAsJsonObject();
                return fromGamemodeJson(root);
            } catch (Exception e) {
                Miniverse.LOGGER.warn("HideAndSeekMapConfig: failed to parse pre-serialized mapConfig, falling back to disk load", e);
            }
        }
        if (mapId == null || mapId.isBlank()) {
            return empty();
        }

        MapEditorExtension extension = MapEditorExtensionRegistry.get(HideAndSeekDefinition.ID).orElse(null);
        if (extension == null) {
            return empty();
        }

        Map<String, List<MapMarker>> markers = MapEditorMarkerStore.loadAll(mapId, extension);
        return fromMarkers(markers);
    }

    public static HideAndSeekMapConfig fromGamemodeJson(JsonObject config) {
        MapEditorExtension extension = MapEditorExtensionRegistry.get(HideAndSeekDefinition.ID).orElse(null);
        if (extension == null) {
            return empty();
        }
        Map<String, List<MapMarker>> markers = MapEditorMarkerStore.loadAll(config, extension);
        return fromMarkers(markers);
    }

    public static HideAndSeekMapConfig empty() {
        return new HideAndSeekMapConfig(List.of(), List.of(), Optional.empty(), List.of());
    }

    private static HideAndSeekMapConfig fromMarkers(Map<String, List<MapMarker>> markers) {
        List<MapPosition> hiderSpawns = markers.getOrDefault(HideAndSeekDefinition.HIDER_SPAWNS, List.of())
            .stream().flatMap(m -> m.points().stream()).toList();

        List<MapPosition> seekerSpawns = markers.getOrDefault(HideAndSeekDefinition.SEEKER_SPAWNS, List.of())
            .stream().flatMap(m -> m.points().stream()).toList();

        Optional<MapPosition> lobby = markers.getOrDefault(HideAndSeekDefinition.WAITING_LOBBY, List.of())
            .stream().flatMap(m -> m.points().stream()).findFirst();

        List<String> disguiseBlocks = markers.getOrDefault(HideAndSeekDefinition.BLOCK_POOL, List.of())
            .stream()
            .map(m -> {
                if (m.properties() != null && m.properties().has("block")) {
                    return m.properties().get("block").getAsString();
                }
                return m.name();
            })
            .filter(s -> !s.isBlank())
            .toList();

        return new HideAndSeekMapConfig(hiderSpawns, seekerSpawns, lobby, disguiseBlocks);
    }
}
