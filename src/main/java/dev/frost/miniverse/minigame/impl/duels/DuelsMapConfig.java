package dev.frost.miniverse.minigame.impl.duels;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapDescriptor;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;
import dev.frost.miniverse.map.editor.MapMarker;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.RegionPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record DuelsMapConfig() {

    public static MapValidationResult validateEditor(MapDescriptor map, JsonObject config) {
        return MapEditorMarkerStore.validate(map, config, DuelsDefinition.EXTENSION);
    }

    public static MapValidationResult validateArenas(MapDescriptor map, JsonObject config, MapEditorExtension extension) {
        MapValidationResult.Builder builder = MapValidationResult.builder();

        MarkerDefinition arenaDef = extension.marker(DuelsDefinition.ARENA).orElseThrow();
        MarkerDefinition p1Def = extension.marker(DuelsDefinition.PLAYER_1_SPAWN).orElseThrow();
        MarkerDefinition p2Def = extension.marker(DuelsDefinition.PLAYER_2_SPAWN).orElseThrow();
        MarkerDefinition specDef = extension.marker(DuelsDefinition.SPECTATOR_SPAWN).orElseThrow();

        List<MapMarker> arenas = MapEditorMarkerStore.load(config, arenaDef);
        List<MapMarker> player1Spawns = new ArrayList<>(MapEditorMarkerStore.load(config, p1Def));
        List<MapMarker> player2Spawns = new ArrayList<>(MapEditorMarkerStore.load(config, p2Def));
        List<MapMarker> spectatorSpawns = new ArrayList<>(MapEditorMarkerStore.load(config, specDef));

        List<String> seenArenaIds = new ArrayList<>();

        for (int i = 0; i < arenas.size(); i++) {
            MapMarker arenaMarker = arenas.get(i);
            String arenaName = arenaMarker.name();
            String arenaId = arenaMarker.properties() != null && arenaMarker.properties().has("id") 
                ? arenaMarker.properties().get("id").getAsString() 
                : arenaMarker.id();

            if (seenArenaIds.contains(arenaId)) {
                builder.error(arenaName + ": Duplicate Arena ID '" + arenaId + "'.");
            } else {
                seenArenaIds.add(arenaId);
            }

            if (arenaMarker.regions().isEmpty()) continue;
            RegionPart bounds = arenaMarker.regions().getFirst();

            // Check overlaps
            for (int j = i + 1; j < arenas.size(); j++) {
                MapMarker otherArena = arenas.get(j);
                if (!otherArena.regions().isEmpty() && bounds.intersects(otherArena.regions().getFirst())) {
                    builder.error(arenaName + ": Overlaps " + otherArena.name());
                }
            }

            // Spawn Validation
            int p1Count = 0;
            int p2Count = 0;
            int specCount = 0;

            for (MapMarker spawn : new ArrayList<>(player1Spawns)) {
                if (!spawn.points().isEmpty() && bounds.contains(spawn.points().getFirst())) {
                    p1Count++;
                    player1Spawns.remove(spawn);
                }
            }

            for (MapMarker spawn : new ArrayList<>(player2Spawns)) {
                if (!spawn.points().isEmpty() && bounds.contains(spawn.points().getFirst())) {
                    p2Count++;
                    player2Spawns.remove(spawn);
                }
            }

            for (MapMarker spawn : new ArrayList<>(spectatorSpawns)) {
                if (!spawn.points().isEmpty() && bounds.contains(spawn.points().getFirst())) {
                    specCount++;
                    spectatorSpawns.remove(spawn);
                }
            }

            if (p1Count == 0) builder.error(arenaName + ": Missing Player 1 Spawn");
            if (p1Count > 1) builder.error(arenaName + ": Multiple Player 1 Spawns");

            if (p2Count == 0) builder.error(arenaName + ": Missing Player 2 Spawn");
            if (p2Count > 1) builder.error(arenaName + ": Multiple Player 2 Spawns");

            if (specCount == 0) builder.error(arenaName + ": Missing Spectator Spawn");
            if (specCount > 1) builder.error(arenaName + ": Multiple Spectator Spawns");
        }

        if (!player1Spawns.isEmpty()) builder.error(player1Spawns.size() + " Player 1 Spawn(s) placed outside of any arena");
        if (!player2Spawns.isEmpty()) builder.error(player2Spawns.size() + " Player 2 Spawn(s) placed outside of any arena");
        if (!spectatorSpawns.isEmpty()) builder.error(spectatorSpawns.size() + " Spectator Spawn(s) placed outside of any arena");

        return builder.build();
    }
}
