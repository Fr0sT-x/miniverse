package dev.frost.miniverse.map.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapDescriptor;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapStore;
import dev.frost.miniverse.map.MapValidationResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MapEditorMarkerStore {
    private MapEditorMarkerStore() {
    }

    public static List<MapMarker> load(String mapId, MapEditorExtension extension, MarkerDefinition definition) {
        JsonObject config = MapStore.readGamemodeConfig(mapId, extension.gameId()).orElseGet(JsonObject::new);
        return load(config, definition);
    }

    public static List<MapMarker> load(JsonObject config, MarkerDefinition definition) {
        List<MapMarker> markers = new ArrayList<>();
        if (config == null || !config.has(definition.configKey())) {
            return markers;
        }
        JsonElement element = config.get(definition.configKey());
        if (element == null || element.isJsonNull()) {
            return markers;
        }
        if (definition.type() == MarkerType.REGION) {
            if (element.isJsonObject()) {
                readRegion(definition, element.getAsJsonObject()).ifPresent(markers::add);
            } else if (element.isJsonArray()) {
                for (JsonElement child : element.getAsJsonArray()) {
                    if (child.isJsonObject()) {
                        readRegion(definition, child.getAsJsonObject()).ifPresent(markers::add);
                    }
                }
            }
            return markers;
        }
        if (element.isJsonArray()) {
            int index = 1;
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonObject()) {
                    markers.add(readPointLike(definition, child.getAsJsonObject(), definition.displayName() + " #" + index));
                    index++;
                }
            }
            return markers;
        }
        if (element.isJsonObject()) {
            markers.add(readPointLike(definition, element.getAsJsonObject(), definition.displayName()));
        }
        return markers;
    }

    public static Map<String, List<MapMarker>> loadAll(String mapId, MapEditorExtension extension) {
        Map<String, List<MapMarker>> result = new LinkedHashMap<>();
        JsonObject config = MapStore.readGamemodeConfig(mapId, extension.gameId()).orElseGet(JsonObject::new);
        for (MarkerDefinition definition : extension.markers()) {
            result.put(definition.key(), load(config, definition));
        }
        return result;
    }

    public static void save(String mapId, MapEditorExtension extension, MarkerDefinition definition, List<MapMarker> markers) throws IOException {
        JsonObject config = MapStore.readGamemodeConfig(mapId, extension.gameId()).orElseGet(JsonObject::new);
        write(config, definition, markers == null ? List.of() : markers);
        MapStore.writeGamemodeConfig(mapId, extension.gameId(), config);
    }

    public static void write(JsonObject config, MarkerDefinition definition, List<MapMarker> markers) {
        if (definition.type() == MarkerType.REGION) {
            if (definition.single()) {
                if (markers.isEmpty()) {
                    config.remove(definition.configKey());
                } else {
                    config.add(definition.configKey(), writeRegion(markers.getFirst()));
                }
            } else {
                JsonArray array = new JsonArray();
                for (MapMarker marker : markers) {
                    array.add(writeRegion(marker));
                }
                config.add(definition.configKey(), array);
            }
            return;
        }
        if (definition.single()) {
            if (markers.isEmpty()) {
                config.remove(definition.configKey());
            } else {
                config.add(definition.configKey(), writePointLike(markers.getFirst(), definition.type() == MarkerType.MULTI_POINT));
            }
            return;
        }
        JsonArray array = new JsonArray();
        for (MapMarker marker : markers) {
            array.add(writePointLike(marker, definition.type() == MarkerType.MULTI_POINT));
        }
        config.add(definition.configKey(), array);
    }

    public static MapValidationResult validate(MapDescriptor map, JsonObject config, MapEditorExtension extension) {
        MapValidationResult.Builder builder = MapValidationResult.builder();
        for (MarkerDefinition definition : extension.markers()) {
            int count = load(config, definition).size();
            if (count < definition.minCount()) {
                if (definition.minCount() == 1) {
                    builder.error("Missing " + definition.displayName());
                } else if (count == 0) {
                    builder.error("No " + definition.displayName() + "s Defined");
                } else {
                    builder.error("At Least " + definition.minCount() + " " + definition.displayName() + "s Required");
                }
            }
        }
        for (MapEditorValidator validator : extension.validators()) {
            MapValidationResult result = validator.validate(map, config, extension);
            for (String error : result.errors()) {
                builder.error(error);
            }
            for (String warning : result.warnings()) {
                builder.warning(warning);
            }
        }
        return builder.build();
    }

    private static MapMarker readPointLike(MarkerDefinition definition, JsonObject json, String fallbackName) {
        if (json.has("points") && json.get("points").isJsonArray()) {
            return MapMarker.fromEditorJson(definition, json, fallbackName);
        }
        List<MapPosition> points = new ArrayList<>();
        if (definition.type() == MarkerType.MULTI_POINT && json.has("path") && json.get("path").isJsonArray()) {
            for (JsonElement child : json.getAsJsonArray("path")) {
                if (child.isJsonObject()) {
                    points.add(MapPosition.fromJson(child.getAsJsonObject(), MapPosition.of(0.0D, 100.0D, 0.0D)));
                }
            }
        } else {
            points.add(MapPosition.fromJson(json, MapPosition.of(0.0D, 100.0D, 0.0D)));
        }
        String name = json.has("name") ? json.get("name").getAsString() : fallbackName;
        String id = json.has("id") ? json.get("id").getAsString() : null;
        JsonObject properties = json.has("properties") && json.get("properties").isJsonObject() ? json.getAsJsonObject("properties").deepCopy() : new JsonObject();
        return new MapMarker(id, definition.key(), name, definition.type(), points, List.of(), properties);
    }

    private static Optional<MapMarker> readRegion(MarkerDefinition definition, JsonObject json) {
        List<RegionPart> parts = new ArrayList<>();
        if (json.has("regions") && json.get("regions").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("regions")) {
                if (element.isJsonObject()) {
                    parts.add(RegionPart.fromJson(element.getAsJsonObject()));
                }
            }
        } else {
            MapPosition min = null;
            MapPosition max = null;
            if (json.has("min") && json.get("min").isJsonObject()) {
                min = MapPosition.fromJson(json.getAsJsonObject("min"), MapPosition.of(0.0D, 100.0D, 0.0D));
            }
            if (json.has("max") && json.get("max").isJsonObject()) {
                max = MapPosition.fromJson(json.getAsJsonObject("max"), MapPosition.of(0.0D, 100.0D, 0.0D));
            }
            if (min == null && json.has("pos1") && json.get("pos1").isJsonObject()) {
                min = MapPosition.fromJson(json.getAsJsonObject("pos1"), MapPosition.of(0.0D, 100.0D, 0.0D));
            }
            if (max == null && json.has("pos2") && json.get("pos2").isJsonObject()) {
                max = MapPosition.fromJson(json.getAsJsonObject("pos2"), MapPosition.of(0.0D, 100.0D, 0.0D));
            }
            if (min != null && max != null) {
                parts.add(new RegionPart(normalizeMin(min, max), normalizeMax(min, max)));
            }
        }
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        String name = json.has("name") ? json.get("name").getAsString() : definition.displayName();
        String id = json.has("id") ? json.get("id").getAsString() : null;
        JsonObject properties = json.has("properties") && json.get("properties").isJsonObject() ? json.getAsJsonObject("properties").deepCopy() : new JsonObject();
        return Optional.of(new MapMarker(id, definition.key(), name, definition.type(), List.of(), parts, properties));
    }

    private static JsonObject writePointLike(MapMarker marker, boolean multiPoint) {
        JsonObject json = new JsonObject();
        json.addProperty("id", marker.id());
        json.addProperty("name", marker.name());
        if (multiPoint) {
            JsonArray path = new JsonArray();
            for (MapPosition point : marker.points()) {
                path.add(point.toJson());
            }
            json.add("path", path);
        } else if (!marker.points().isEmpty()) {
            JsonObject point = marker.points().getFirst().toJson();
            for (String key : point.keySet()) {
                json.add(key, point.get(key));
            }
        }
        if (marker.properties() != null && !marker.properties().isEmpty()) {
            json.add("properties", marker.properties().deepCopy());
        }
        return json;
    }

    private static JsonObject writeRegion(MapMarker marker) {
        JsonObject json = new JsonObject();
        json.addProperty("id", marker.id());
        json.addProperty("name", marker.name());
        
        JsonArray regions = new JsonArray();
        for (RegionPart part : marker.regions()) {
            regions.add(part.toJson());
        }
        json.add("regions", regions);
        
        if (marker.regions().size() == 1) {
            json.add("min", marker.regions().getFirst().min().toJson());
            json.add("max", marker.regions().getFirst().max().toJson());
        }
        
        if (marker.properties() != null && !marker.properties().isEmpty()) {
            json.add("properties", marker.properties().deepCopy());
        }
        return json;
    }

    private static MapPosition normalizeMin(MapPosition a, MapPosition b) {
        return new MapPosition(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()), 0.0F, 0.0F);
    }

    private static MapPosition normalizeMax(MapPosition a, MapPosition b) {
        return new MapPosition(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()), 0.0F, 0.0F);
    }
}
