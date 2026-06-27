package dev.frost.miniverse.minigame.impl.bedwars;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapDescriptor;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public record BedwarsMapConfig(
    Map<String, BedwarsTeamConfig> teams,
    List<MapPosition> midDiamondGens,
    List<MapPosition> midEmeraldGens,
    List<MapPosition> shopNpcs,
    List<MapPosition> upgradeNpcs,
    @Nullable MapPosition spectatorSpawn
) {
    public BedwarsMapConfig {
        teams = Map.copyOf(teams);
        midDiamondGens = List.copyOf(midDiamondGens);
        midEmeraldGens = List.copyOf(midEmeraldGens);
        shopNpcs = List.copyOf(shopNpcs);
        upgradeNpcs = List.copyOf(upgradeNpcs);
    }

    public static class BedwarsTeamConfig {
        public String teamId;
        public String name;
        public List<MapPosition> spawns = new ArrayList<>();
        public BlockPos bedPos;
        public List<MapPosition> ironGens = new ArrayList<>();
        public List<MapPosition> goldGens = new ArrayList<>();

        public BedwarsTeamConfig(String teamId, String name) {
            this.teamId = teamId;
            this.name = name;
        }
    }

    public static BedwarsMapConfig fromJson(JsonObject json) {
        if (json == null) {
            return new BedwarsMapConfig(Map.of(), List.of(), List.of(), List.of(), List.of(), null);
        }

        Map<String, BedwarsTeamConfig> teams = new HashMap<>();

        if (json.has("teamConfigs") && json.get("teamConfigs").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("teamConfigs")) {
                if (element.isJsonObject()) {
                    JsonObject markerObj = element.getAsJsonObject();
                    String name = markerObj.has("name") ? markerObj.get("name").getAsString() : "Unknown Team";
                    // Generate a safe team ID from the name if the client didn't supply one in properties
                    String teamId = extractTeamId(markerObj, name.toLowerCase().replaceAll("[^a-z0-9]", "_"));
                    
                    if (teamId != null && !teamId.isBlank()) {
                        teams.put(teamId, new BedwarsTeamConfig(teamId, name));
                    }
                }
            }
        }

        parseTeamMarkers(json, "teamSpawns", teams, (config, pos) -> config.spawns.add(pos));
        parseTeamMarkers(json, "teamBeds", teams, (config, pos) -> config.bedPos = net.minecraft.util.math.BlockPos.ofFloored(pos.x(), pos.y(), pos.z()));
        parseTeamMarkers(json, "islandIronGens", teams, (config, pos) -> config.ironGens.add(pos));
        parseTeamMarkers(json, "islandGoldGens", teams, (config, pos) -> config.goldGens.add(pos));

        return new BedwarsMapConfig(
            teams,
            parsePoints(json, "midDiamondGens"),
            parsePoints(json, "midEmeraldGens"),
            parsePoints(json, "shopNpcs"),
            parsePoints(json, "upgradeNpcs"),
            parseSinglePoint(json, "spectatorSpawn")
        );
    }

    public static BedwarsMapConfig fromJsonString(String value) {
        try {
            JsonElement element = com.google.gson.JsonParser.parseString(value == null ? "{}" : value);
            return element.isJsonObject() ? fromJson(element.getAsJsonObject()) : new BedwarsMapConfig(Map.of(), List.of(), List.of(), List.of(), List.of(), null);
        } catch (IllegalStateException ignored) {
            return new BedwarsMapConfig(Map.of(), List.of(), List.of(), List.of(), List.of(), null);
        }
    }

    @Nullable
    public String findBedTeam(BlockPos pos) {
        for (BedwarsTeamConfig config : teams.values()) {
            if (config.bedPos != null && config.bedPos.equals(pos)) {
                return config.teamId;
            }
        }
        return null;
    }

    private static String extractTeamId(JsonObject markerObj, String fallback) {
        if (markerObj.has("properties") && markerObj.get("properties").isJsonObject()) {
            JsonObject props = markerObj.getAsJsonObject("properties");
            if (props.has("teamId")) {
                return props.get("teamId").getAsString();
            }
        }
        return fallback;
    }

    private static void parseTeamMarkers(JsonObject json, String key, Map<String, BedwarsTeamConfig> teams, BiConsumer<BedwarsTeamConfig, MapPosition> action) {
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(key)) {
                if (element.isJsonObject()) {
                    JsonObject markerObj = element.getAsJsonObject();
                    String teamId = extractTeamId(markerObj, null);
                    if (teamId != null && teams.containsKey(teamId)) {
                        MapPosition pos = extractPosition(markerObj);
                        if (pos != null) {
                            action.accept(teams.get(teamId), pos);
                        }
                    }
                }
            }
        }
    }

    private static List<MapPosition> parsePoints(JsonObject json, String key) {
        List<MapPosition> points = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(key)) {
                if (element.isJsonObject()) {
                    MapPosition pos = extractPosition(element.getAsJsonObject());
                    if (pos != null) {
                        points.add(pos);
                    }
                }
            }
        }
        return points;
    }

    @Nullable
    private static MapPosition parseSinglePoint(JsonObject json, String key) {
        List<MapPosition> points = parsePoints(json, key);
        return points.isEmpty() ? null : points.get(0);
    }

    @Nullable
    private static MapPosition extractPosition(JsonObject element) {
        if (element.has("points")) {
            JsonElement pointsArray = element.get("points");
            if (pointsArray.isJsonArray() && !pointsArray.getAsJsonArray().isEmpty()) {
                JsonElement firstPoint = pointsArray.getAsJsonArray().get(0);
                if (firstPoint.isJsonObject()) {
                    return MapPosition.fromJson(firstPoint.getAsJsonObject(), MapPosition.of(0.0D, 100.0D, 0.0D));
                }
            }
        } else {
            return MapPosition.fromJson(element, MapPosition.of(0.0D, 100.0D, 0.0D));
        }
        return null;
    }

    public MapValidationResult validate() {
        MapValidationResult.Builder builder = MapValidationResult.builder();
        if (this.teams.size() < 2) {
            builder.error("Map must have at least 2 teams defined.");
        }
        for (BedwarsTeamConfig team : this.teams.values()) {
            if (team.spawns.isEmpty()) {
                builder.error("Team '" + team.name + "' is missing a spawn point.");
            }
            if (team.bedPos == null) {
                builder.error("Team '" + team.name + "' is missing a bed.");
            }
        }
        if (this.spectatorSpawn == null) {
            builder.error("Missing Spectator Spawn");
        }
        return builder.build();
    }

    public static MapValidationResult validateEditor(MapDescriptor map, JsonObject config) {
        return MapEditorMarkerStore.validate(map, config, BedwarsDefinition.EXTENSION);
    }
}
