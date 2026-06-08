package dev.frost.miniverse.map.editor;

import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapPosition;

public record RegionPart(MapPosition min, MapPosition max) {

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.add("min", this.min.toJson());
        json.add("max", this.max.toJson());
        return json;
    }

    public static RegionPart fromJson(JsonObject json) {
        MapPosition min = json.has("min") ? MapPosition.fromJson(json.getAsJsonObject("min"), MapPosition.of(0, 0, 0)) : MapPosition.of(0, 0, 0);
        MapPosition max = json.has("max") ? MapPosition.fromJson(json.getAsJsonObject("max"), MapPosition.of(0, 0, 0)) : MapPosition.of(0, 0, 0);
        return new RegionPart(min, max);
    }

    public boolean contains(MapPosition point) {
        return point.x() >= min.x() && point.x() <= max.x() &&
               point.y() >= min.y() && point.y() <= max.y() &&
               point.z() >= min.z() && point.z() <= max.z();
    }

    public boolean intersects(RegionPart other) {
        return this.min.x() <= other.max.x() && this.max.x() >= other.min.x() &&
               this.min.y() <= other.max.y() && this.max.y() >= other.min.y() &&
               this.min.z() <= other.max.z() && this.max.z() >= other.min.z();
    }
}
