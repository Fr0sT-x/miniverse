package dev.frost.miniverse.minigame.core.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MinigameSerializationUtil {
    private MinigameSerializationUtil() {}

    public static JsonArray writeUuidArray(Collection<UUID> uuids) {
        JsonArray array = new JsonArray();
        for (UUID uuid : uuids) {
            array.add(uuid.toString());
        }
        return array;
    }

    public static List<UUID> readUuidArray(JsonObject root, String key) {
        List<UUID> uuids = new ArrayList<>();
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            return uuids;
        }
        for (JsonElement element : root.getAsJsonArray(key)) {
            try {
                uuids.add(UUID.fromString(element.getAsString()));
            } catch (RuntimeException ignored) {
            }
        }
        return uuids;
    }

    public static JsonObject writeUuidUuidMap(Map<UUID, UUID> map) {
        JsonObject object = new JsonObject();
        map.forEach((key, value) -> object.addProperty(key.toString(), value.toString()));
        return object;
    }

    public static Map<UUID, UUID> readUuidUuidMap(JsonObject root, String key) {
        Map<UUID, UUID> map = new ConcurrentHashMap<>();
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return map;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(key).entrySet()) {
            try {
                map.put(UUID.fromString(entry.getKey()), UUID.fromString(entry.getValue().getAsString()));
            } catch (RuntimeException ignored) {
            }
        }
        return map;
    }

    public static JsonObject writeUuidIntMap(Map<UUID, Integer> map) {
        JsonObject object = new JsonObject();
        map.forEach((key, value) -> object.addProperty(key.toString(), value));
        return object;
    }

    public static Map<UUID, Integer> readUuidIntMap(JsonObject root, String key) {
        Map<UUID, Integer> map = new ConcurrentHashMap<>();
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return map;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(key).entrySet()) {
            try {
                map.put(UUID.fromString(entry.getKey()), entry.getValue().getAsInt());
            } catch (RuntimeException ignored) {
            }
        }
        return map;
    }

    public static JsonObject writeUuidLongMap(Map<UUID, Long> map) {
        JsonObject object = new JsonObject();
        map.forEach((key, value) -> object.addProperty(key.toString(), value));
        return object;
    }

    public static Map<UUID, Long> readUuidLongMap(JsonObject root, String key) {
        Map<UUID, Long> map = new ConcurrentHashMap<>();
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return map;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(key).entrySet()) {
            try {
                map.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
            } catch (RuntimeException ignored) {
            }
        }
        return map;
    }
}
