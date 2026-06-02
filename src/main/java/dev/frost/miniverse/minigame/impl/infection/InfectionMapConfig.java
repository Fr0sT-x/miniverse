package dev.frost.miniverse.minigame.impl.infection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;

import java.util.ArrayList;
import java.util.List;

public record InfectionMapConfig(List<MapPosition> spawnPoints) {
    public InfectionMapConfig {
        spawnPoints = spawnPoints == null ? List.of() : List.copyOf(spawnPoints);
    }

    public static InfectionMapConfig fromJson(JsonObject json) {
        List<MapPosition> spawns = new ArrayList<>();
        if (json != null && json.has("spawnPoints") && json.get("spawnPoints").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("spawnPoints")) {
                if (element.isJsonObject()) {
                    spawns.add(MapPosition.fromJson(element.getAsJsonObject(), MapPosition.of(0.0D, 100.0D, 0.0D)));
                }
            }
        }
        return new InfectionMapConfig(spawns);
    }

    public static InfectionMapConfig fromJsonString(String value) {
        try {
            JsonElement element = com.google.gson.JsonParser.parseString(value == null ? "{}" : value);
            return element.isJsonObject() ? fromJson(element.getAsJsonObject()) : new InfectionMapConfig(List.of());
        } catch (IllegalStateException ignored) {
            return new InfectionMapConfig(List.of());
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonArray spawns = new JsonArray();
        for (MapPosition spawn : this.spawnPoints) {
            spawns.add(spawn.toJson());
        }
        json.add("spawnPoints", spawns);
        return json;
    }

    public MapValidationResult validate() {
        MapValidationResult.Builder result = MapValidationResult.builder();
        if (this.spawnPoints.isEmpty()) {
            result.error("No Spawn Points Configured");
        } else if (this.spawnPoints.size() < 2) {
            result.error("At Least Two Spawn Points Required");
        }
        return result.build();
    }
}
