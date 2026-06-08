package dev.frost.miniverse.minigame.impl.murdermystery;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;
import dev.frost.miniverse.map.editor.MapMarker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record MurderMysteryMapConfig(
    List<MapPosition> spawnPoints,
    List<CoinSpawnPoint> coinSpawns,
    List<MapPosition> shopNpcs
) {
    public record CoinSpawnPoint(MapPosition position, int weight, boolean enabled, List<String> tags) {}

    public static MurderMysteryMapConfig load(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return new MurderMysteryMapConfig(List.of(), List.of(), List.of());
        }

        MapEditorExtension extension = MapEditorExtensionRegistry.get(MurderMysteryDefinition.ID).orElse(null);
        if (extension == null) {
            return new MurderMysteryMapConfig(List.of(), List.of(), List.of());
        }
        
        Map<String, List<MapMarker>> markers = MapEditorMarkerStore.loadAll(mapId, extension);
        
        List<MapPosition> spawns = markers.getOrDefault("spawn_point", List.of())
            .stream().flatMap(m -> m.points().stream()).toList();
            
        List<MapPosition> shops = markers.getOrDefault("shop_npc", List.of())
            .stream().flatMap(m -> m.points().stream()).toList();
            
        List<CoinSpawnPoint> coins = new ArrayList<>();
        for (MapMarker marker : markers.getOrDefault("coin_spawn", List.of())) {
            for (MapPosition pos : marker.points()) {
                int weight = marker.properties().has("weight") ? marker.properties().get("weight").getAsInt() : 1;
                boolean enabled = !marker.properties().has("enabled") || marker.properties().get("enabled").getAsBoolean();
                List<String> tags = new ArrayList<>();
                if (marker.properties().has("tags") && marker.properties().get("tags").isJsonArray()) {
                    marker.properties().getAsJsonArray("tags").forEach(e -> tags.add(e.getAsString()));
                }
                coins.add(new CoinSpawnPoint(pos, weight, enabled, tags));
            }
        }
        
        return new MurderMysteryMapConfig(spawns, coins, shops);
    }
}
