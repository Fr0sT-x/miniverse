package dev.frost.miniverse.minigame.impl.ctf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.frost.miniverse.map.MapDescriptor;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public record CaptureTheFlagMapConfig(
    Map<String, CtfTeamConfig> teams,
    List<MapPosition> powerupLocations,
    Integer voidLevelRef,
    Integer heightLimitRef
) {
    public CaptureTheFlagMapConfig(Map<String, CtfTeamConfig> teams, List<MapPosition> powerupLocations, Integer voidLevelRef) {
        this(teams, powerupLocations, voidLevelRef, null);
    }

    public CaptureTheFlagMapConfig {
        teams = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(teams));
        powerupLocations = List.copyOf(powerupLocations);
    }

    public static class CtfTeamConfig {
        public final String teamId;
        public final String name;
        public final Formatting color;
        public final List<MapPosition> spawns = new ArrayList<>();
        public BlockPos flagPos;
        public BlockPos dropoffPos;
        public final List<MapPosition> shopNpcs = new ArrayList<>();

        public CtfTeamConfig(String teamId, String name, Formatting color) {
            this.teamId = teamId;
            this.name = name;
            this.color = color;
        }

        public BlockPos getEffectiveDropoffPos() {
            return this.dropoffPos != null ? this.dropoffPos : this.flagPos;
        }
    }

    public static CaptureTheFlagMapConfig fromJson(JsonObject json) {
        if (json == null) {
            return new CaptureTheFlagMapConfig(Map.of(), List.of(), null);
        }

        Map<String, CtfTeamConfig> teams = new java.util.LinkedHashMap<>();

        if (json.has("teamConfigs") && json.get("teamConfigs").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("teamConfigs")) {
                if (element.isJsonObject()) {
                    JsonObject markerObj = element.getAsJsonObject();
                    String name = markerObj.has("name") ? markerObj.get("name").getAsString() : "Unknown Team";
                    String teamId = markerObj.has("id") ? markerObj.get("id").getAsString() : extractTeamId(markerObj, name.toLowerCase().replaceAll("[^a-z0-9]", "_"));
                    
                    if (teamId != null && !teamId.isBlank()) {
                        Formatting color = null;
                        if (markerObj.has("properties")) {
                            JsonObject props = markerObj.getAsJsonObject("properties");
                            if (props.has("color")) {
                                color = Formatting.byName(props.get("color").getAsString());
                            }
                        }
                        teams.put(teamId, new CtfTeamConfig(teamId, name, color));
                    }
                }
            }
        }

        parseTeamMarkers(json, "teamSpawns", teams, (config, pos) -> config.spawns.add(pos));
        parseTeamMarkers(json, "teamFlags", teams, (config, pos) -> config.flagPos = BlockPos.ofFloored(pos.x(), pos.y(), pos.z()));
        parseTeamMarkers(json, "teamDropoffs", teams, (config, pos) -> config.dropoffPos = BlockPos.ofFloored(pos.x(), pos.y(), pos.z()));
        parseTeamMarkers(json, "shopNpcs", teams, (config, pos) -> config.shopNpcs.add(pos));

        return new CaptureTheFlagMapConfig(
            teams,
            parsePoints(json, "powerupLocations"),
            parseVoidLevelRef(json, "voidLevel"),
            parseVoidLevelRef(json, "heightLimit")
        );
    }

    public static CaptureTheFlagMapConfig fromJsonString(String value) {
        try {
            JsonElement element = JsonParser.parseString(value == null ? "{}" : value);
            return element.isJsonObject() ? fromJson(element.getAsJsonObject()) : new CaptureTheFlagMapConfig(Map.of(), List.of(), null, null);
        } catch (Exception ignored) {
            return new CaptureTheFlagMapConfig(Map.of(), List.of(), null, null);
        }
    }

    @Nullable
    public String findFlagTeam(BlockPos pos) {
        for (CtfTeamConfig config : teams.values()) {
            if (config.flagPos != null) {
                int dx = Math.abs(config.flagPos.getX() - pos.getX());
                int dy = Math.abs(config.flagPos.getY() - pos.getY());
                int dz = Math.abs(config.flagPos.getZ() - pos.getZ());
                if (dx <= 2 && dy <= 3 && dz <= 2) {
                    return config.teamId;
                }
            }
        }
        return null;
    }

    @Nullable
    public String findDropoffTeam(BlockPos pos) {
        for (CtfTeamConfig config : teams.values()) {
            BlockPos dropoff = config.getEffectiveDropoffPos();
            if (dropoff != null) {
                int dx = Math.abs(dropoff.getX() - pos.getX());
                int dy = Math.abs(dropoff.getY() - pos.getY());
                int dz = Math.abs(dropoff.getZ() - pos.getZ());
                if (dx <= 2 && dy <= 3 && dz <= 2) {
                    return config.teamId;
                }
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

    private static CtfTeamConfig findMatchingTeam(Map<String, CtfTeamConfig> teams, String teamId, MapPosition pos) {
        if (teamId != null && !teamId.isBlank()) {
            if (teams.containsKey(teamId)) {
                return teams.get(teamId);
            }
            for (Map.Entry<String, CtfTeamConfig> entry : teams.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(teamId)
                    || entry.getValue().name.equalsIgnoreCase(teamId)
                    || (entry.getValue().color != null && entry.getValue().color.getName().equalsIgnoreCase(teamId))) {
                    return entry.getValue();
                }
            }
        }
        if (teams.size() == 1) {
            return teams.values().iterator().next();
        }
        if (pos != null) {
            CtfTeamConfig closest = null;
            double minDist = Double.MAX_VALUE;
            for (CtfTeamConfig cfg : teams.values()) {
                for (MapPosition spawn : cfg.spawns) {
                    double dist = Math.pow(spawn.x() - pos.x(), 2) + Math.pow(spawn.z() - pos.z(), 2);
                    if (dist < minDist) {
                        minDist = dist;
                        closest = cfg;
                    }
                }
            }
            if (closest != null && minDist < 10000.0) {
                return closest;
            }
        }
        return null;
    }

    private static void parseTeamMarkers(JsonObject json, String key, Map<String, CtfTeamConfig> teams, BiConsumer<CtfTeamConfig, MapPosition> action) {
        if (!json.has(key)) {
            return;
        }
        JsonElement elem = json.get(key);
        if (elem.isJsonArray()) {
            for (JsonElement element : elem.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    parseSingleTeamMarker(element.getAsJsonObject(), teams, action);
                }
            }
        } else if (elem.isJsonObject()) {
            parseSingleTeamMarker(elem.getAsJsonObject(), teams, action);
        }
    }

    private static void parseSingleTeamMarker(JsonObject markerObj, Map<String, CtfTeamConfig> teams, BiConsumer<CtfTeamConfig, MapPosition> action) {
        String teamId = extractTeamId(markerObj, null);
        MapPosition pos = extractPosition(markerObj);
        CtfTeamConfig team = findMatchingTeam(teams, teamId, pos);
        if (team != null && pos != null) {
            action.accept(team, pos);
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
        for (CtfTeamConfig team : this.teams.values()) {
            if (team.spawns.isEmpty()) {
                builder.error("Team '" + team.name + "' is missing a spawn point.");
            }
            if (team.flagPos == null) {
                builder.error("Team '" + team.name + "' is missing a flag post.");
            }
        }
        if (this.voidLevelRef == null) {
            builder.error("Missing Void Death Level Reference");
        }
        return builder.build();
    }

    private static Integer parseVoidLevelRef(JsonObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        JsonElement value = json.get(key);
        if (value.isJsonObject()) {
            JsonObject obj = value.getAsJsonObject();
            if (obj.has("points")) {
                JsonElement pointsArray = obj.get("points");
                if (pointsArray.isJsonArray() && !pointsArray.getAsJsonArray().isEmpty()) {
                    JsonElement firstPoint = pointsArray.getAsJsonArray().get(0);
                    if (firstPoint.isJsonObject()) {
                        MapPosition pos = MapPosition.fromJson(firstPoint.getAsJsonObject(), MapPosition.of(0.0D, 100.0D, 0.0D));
                        return (int) pos.y();
                    }
                }
            } else {
                MapPosition pos = MapPosition.fromJson(obj, MapPosition.of(0.0D, 100.0D, 0.0D));
                return (int) pos.y();
            }
        }
        return null;
    }

    public static MapValidationResult validateEditor(MapDescriptor map, JsonObject config) {
        return MapEditorMarkerStore.validate(map, config, CaptureTheFlagDefinition.EXTENSION);
    }
}
