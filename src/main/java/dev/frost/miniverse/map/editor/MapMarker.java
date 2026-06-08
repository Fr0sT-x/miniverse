package dev.frost.miniverse.map.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record MapMarker(String id, String definitionKey, String name, MarkerType type, List<MapPosition> points, List<RegionPart> regions, JsonObject properties) {
    public MapMarker {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        definitionKey = MarkerDefinition.normalizeKey(definitionKey);
        name = name == null || name.isBlank() ? definitionKey : name.trim();
        type = type == null ? MarkerType.POINT : type;
        points = points == null ? List.of() : List.copyOf(points);
        regions = regions == null ? List.of() : List.copyOf(regions);
        properties = properties == null ? new JsonObject() : properties;
    }

    public static MapMarker point(MarkerDefinition definition, String name, MapPosition position) {
        return new MapMarker(UUID.randomUUID().toString(), definition.key(), name, definition.type(), List.of(position), List.of(), new JsonObject());
    }

    public JsonObject toEditorJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", this.id);
        json.addProperty("definitionKey", this.definitionKey);
        json.addProperty("name", this.name);
        json.addProperty("type", this.type.name());
        JsonArray array = new JsonArray();
        for (MapPosition point : this.points) {
            array.add(point.toJson());
        }
        json.add("points", array);
        
        JsonArray regionArray = new JsonArray();
        for (RegionPart region : this.regions) {
            regionArray.add(region.toJson());
        }
        json.add("regions", regionArray);
        
        if (this.properties != null && !this.properties.isEmpty()) {
            json.add("properties", this.properties.deepCopy());
        }
        return json;
    }

    public static MapMarker fromEditorJson(MarkerDefinition definition, JsonObject json, String fallbackName) {
        List<MapPosition> points = new ArrayList<>();
        if (json != null && json.has("points") && json.get("points").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("points")) {
                if (element.isJsonObject()) {
                    points.add(MapPosition.fromJson(element.getAsJsonObject(), MapPosition.of(0.0D, 100.0D, 0.0D)));
                }
            }
        }
        List<RegionPart> regions = new ArrayList<>();
        if (json != null && json.has("regions") && json.get("regions").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("regions")) {
                if (element.isJsonObject()) {
                    regions.add(RegionPart.fromJson(element.getAsJsonObject()));
                }
            }
        }
        JsonObject properties = new JsonObject();
        if (json != null && json.has("properties") && json.get("properties").isJsonObject()) {
            properties = json.getAsJsonObject("properties").deepCopy();
        }
        return new MapMarker(
            json != null && json.has("id") ? json.get("id").getAsString() : UUID.randomUUID().toString(),
            definition.key(),
            json != null && json.has("name") ? json.get("name").getAsString() : fallbackName,
            definition.type(),
            points,
            regions,
            properties
        );
    }
}
