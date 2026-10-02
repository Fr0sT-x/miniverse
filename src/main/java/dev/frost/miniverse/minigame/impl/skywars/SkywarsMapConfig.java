package dev.frost.miniverse.minigame.impl.skywars;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;
import dev.frost.miniverse.map.editor.MapMarker;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record SkywarsMapConfig(
    List<MapPosition> islandSpawns,
    List<BlockPos> islandChests,
    List<BlockPos> midChests,
    Optional<MapPosition> waitingLobby,
    double voidLevel
) {
    public static final double DEFAULT_VOID_LEVEL = 0.0;

    public static SkywarsMapConfig load(String mapId, String preSerializedJson) {
        if (preSerializedJson != null && !preSerializedJson.isBlank()) {
            try {
                JsonObject root = JsonParser.parseString(preSerializedJson).getAsJsonObject();
                return fromGamemodeJson(root);
            } catch (Exception e) {
                Miniverse.LOGGER.warn("SkywarsMapConfig: failed to parse pre-serialized mapConfig, falling back to disk load", e);
            }
        }
        if (mapId == null || mapId.isBlank()) {
            return empty();
        }

        MapEditorExtension extension = MapEditorExtensionRegistry.get(SkywarsDefinition.ID).orElse(null);
        if (extension == null) {
            return empty();
        }

        Map<String, List<MapMarker>> markers = MapEditorMarkerStore.loadAll(mapId, extension);
        return fromMarkers(markers);
    }

    public static SkywarsMapConfig fromGamemodeJson(JsonObject config) {
        MapEditorExtension extension = MapEditorExtensionRegistry.get(SkywarsDefinition.ID).orElse(null);
        if (extension == null) {
            return empty();
        }
        Map<String, List<MapMarker>> markers = MapEditorMarkerStore.loadAll(config, extension);
        return fromMarkers(markers);
    }

    public static SkywarsMapConfig empty() {
        return new SkywarsMapConfig(List.of(), List.of(), List.of(), Optional.empty(), DEFAULT_VOID_LEVEL);
    }

    private static SkywarsMapConfig fromMarkers(Map<String, List<MapMarker>> markers) {
        List<MapPosition> spawns = markers.getOrDefault(SkywarsDefinition.SPAWNS, List.of())
            .stream().flatMap(m -> m.points().stream()).toList();

        List<BlockPos> islandChests = toBlockPositions(markers.getOrDefault(SkywarsDefinition.ISLAND_CHESTS, List.of()));
        List<BlockPos> midChests = toBlockPositions(markers.getOrDefault(SkywarsDefinition.MID_CHESTS, List.of()));

        Optional<MapPosition> lobby = markers.getOrDefault(SkywarsDefinition.WAITING_LOBBY, List.of())
            .stream().flatMap(m -> m.points().stream()).findFirst();

        double voidLevel = DEFAULT_VOID_LEVEL;
        List<MapMarker> voidMarkers = markers.getOrDefault(SkywarsDefinition.VOID_LEVEL, List.of());
        if (!voidMarkers.isEmpty() && !voidMarkers.getFirst().points().isEmpty()) {
            voidLevel = voidMarkers.getFirst().points().getFirst().y();
        }

        return new SkywarsMapConfig(spawns, islandChests, midChests, lobby, voidLevel);
    }

    private static List<BlockPos> toBlockPositions(List<MapMarker> markers) {
        List<BlockPos> positions = new ArrayList<>();
        for (MapMarker marker : markers) {
            for (MapPosition point : marker.points()) {
                positions.add(BlockPos.ofFloored(point.x(), point.y(), point.z()));
            }
        }
        return positions;
    }
}
