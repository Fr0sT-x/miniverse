package dev.frost.miniverse.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;

public record MapMetadata(
    String id,
    String name,
    String description,
    MapPosition editorSpawn,
    List<String> tags
) {
    public MapMetadata {
        id = sanitizeId(id);
        name = name == null || name.isBlank() ? id : name.trim();
        description = description == null ? "" : description.trim();
        editorSpawn = editorSpawn == null ? MapPosition.of(0.0D, 100.0D, 0.0D) : editorSpawn;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public static MapMetadata defaults(String id, String name) {
        return new MapMetadata(id, name, "", MapPosition.of(0.0D, 100.0D, 0.0D), List.of());
    }

    public static MapMetadata fromJson(String folderId, JsonObject json) {
        if (json == null) {
            return defaults(folderId, folderId);
        }
        List<String> tags = new ArrayList<>();
        if (json.has("tags") && json.get("tags").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("tags")) {
                if (!element.isJsonNull()) {
                    String tag = element.getAsString().trim();
                    if (!tag.isBlank()) {
                        tags.add(tag);
                    }
                }
            }
        }
        String id = string(json, "id", folderId);
        return new MapMetadata(
            id.isBlank() ? folderId : id,
            string(json, "name", folderId),
            string(json, "description", ""),
            MapPosition.fromJson(json.has("editorSpawn") && json.get("editorSpawn").isJsonObject() ? json.getAsJsonObject("editorSpawn") : null, MapPosition.of(0.0D, 100.0D, 0.0D)),
            tags
        );
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", this.id);
        json.addProperty("name", this.name);
        json.addProperty("description", this.description);
        json.add("editorSpawn", this.editorSpawn.toJson());
        JsonArray tagArray = new JsonArray();
        for (String tag : this.tags) {
            tagArray.add(tag);
        }
        json.add("tags", tagArray);
        return json;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", this.id);
        nbt.putString("name", this.name);
        nbt.putString("description", this.description);
        nbt.put("editorSpawn", this.editorSpawn.toNbt());
        NbtList tagList = new NbtList();
        for (String tag : this.tags) {
            tagList.add(NbtString.of(tag));
        }
        nbt.put("tags", tagList);
        return nbt;
    }

    static String sanitizeId(String value) {
        String id = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return id.isBlank() ? "map" : id;
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }
}
