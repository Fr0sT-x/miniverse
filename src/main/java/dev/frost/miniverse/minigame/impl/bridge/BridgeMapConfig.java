package dev.frost.miniverse.minigame.impl.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapDescriptor;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;

import java.util.ArrayList;
import java.util.List;

public record BridgeMapConfig(List<MapPosition> redSpawns, List<MapPosition> blueSpawns, Integer voidLevelRef, Integer heightLimitRef) {
    public BridgeMapConfig {
        redSpawns = redSpawns == null ? List.of() : List.copyOf(redSpawns);
        blueSpawns = blueSpawns == null ? List.of() : List.copyOf(blueSpawns);
    }

    public static BridgeMapConfig fromJson(JsonObject json) {
        if (json == null) {
            return new BridgeMapConfig(List.of(), List.of(), null, null);
        }
        return new BridgeMapConfig(
            parsePoints(json, "redTeamSpawns"),
            parsePoints(json, "blueTeamSpawns"),
            parseVoidLevelRef(json, "voidLevel"),
            parseVoidLevelRef(json, "heightLimit")
        );
    }

    public static BridgeMapConfig fromJsonString(String value) {
        try {
            JsonElement element = com.google.gson.JsonParser.parseString(value == null ? "{}" : value);
            return element.isJsonObject() ? fromJson(element.getAsJsonObject()) : new BridgeMapConfig(List.of(), List.of(), null, null);
        } catch (IllegalStateException ignored) {
            return new BridgeMapConfig(List.of(), List.of(), null, null);
        }
    }

    private static List<MapPosition> parsePoints(JsonObject json, String key) {
        List<MapPosition> points = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(key)) {
                if (element.isJsonObject()) {
                    if (element.getAsJsonObject().has("points")) {
                        // Point-like marker JSON structure (id, definitionKey, name, type, points)
                        JsonElement pointsArray = element.getAsJsonObject().get("points");
                        if (pointsArray.isJsonArray() && !pointsArray.getAsJsonArray().isEmpty()) {
                            JsonElement firstPoint = pointsArray.getAsJsonArray().get(0);
                            if (firstPoint.isJsonObject()) {
                                points.add(MapPosition.fromJson(firstPoint.getAsJsonObject(), MapPosition.of(0.0D, 100.0D, 0.0D)));
                            }
                        }
                    } else {
                        // Raw MapPosition structure
                        points.add(MapPosition.fromJson(element.getAsJsonObject(), MapPosition.of(0.0D, 100.0D, 0.0D)));
                    }
                }
            }
        }
        return points;
    }

    private static Integer parseVoidLevelRef(JsonObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        JsonElement value = json.get(key);

        // Single object format: { "x": ..., "y": ..., "z": ... }
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

        // Array format: [ { ... }, ... ]
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    JsonObject obj = element.getAsJsonObject();
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
            }
        }
        return null;
    }

    public MapValidationResult validate() {
        MapValidationResult.Builder builder = MapValidationResult.builder();
        if (this.redSpawns.isEmpty()) {
            builder.error("Missing Red Team Spawn");
        }
        if (this.blueSpawns.isEmpty()) {
            builder.error("Missing Blue Team Spawn");
        }
        if (this.voidLevelRef == null) {
            builder.error("Missing Void Death Level Reference");
        }
        return builder.build();
    }

    public static MapValidationResult validateEditor(MapDescriptor map, JsonObject config) {
        return MapEditorMarkerStore.validate(map, config, BridgeDefinition.EXTENSION);
    }
}
