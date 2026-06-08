package dev.frost.miniverse.minigame.impl.duels;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.arena.ArenaRegion;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record DuelsMetadata(
    List<String> supportedKits,
    List<ArenaRegion> arenas
) {
    public static DuelsMetadata fromJson(JsonObject config) {
        List<String> kits = new ArrayList<>();
        if (config.has("supported_kits")) {
            for (JsonElement el : config.getAsJsonArray("supported_kits")) {
                kits.add(el.getAsString());
            }
        }

        List<ArenaRegion> arenas = new ArrayList<>();
        if (config.has("arenas")) {
            for (JsonElement el : config.getAsJsonArray("arenas")) {
                JsonObject arenaObj = el.getAsJsonObject();
                String id = arenaObj.has("id") ? arenaObj.get("id").getAsString() : "unknown";
                
                Vec3d min = Vec3d.ZERO;
                Vec3d max = Vec3d.ZERO;
                if (arenaObj.has("bounds")) {
                    JsonObject bounds = arenaObj.getAsJsonObject("bounds");
                    min = parseVec3d(bounds.getAsJsonObject("min"));
                    max = parseVec3d(bounds.getAsJsonObject("max"));
                }

                Map<String, Vec3d> spawns = new HashMap<>();
                if (arenaObj.has("spawns")) {
                    JsonObject spawnsObj = arenaObj.getAsJsonObject("spawns");
                    for (String key : spawnsObj.keySet()) {
                        spawns.put(key, parseVec3d(spawnsObj.getAsJsonObject(key)));
                    }
                }

                List<String> tags = new ArrayList<>();
                if (arenaObj.has("tags")) {
                    for (JsonElement tagEl : arenaObj.getAsJsonArray("tags")) {
                        tags.add(tagEl.getAsString());
                    }
                }

                arenas.add(new ArenaRegion(id, min, max, spawns, tags));
            }
        }

        DuelsMetadata metadata = new DuelsMetadata(kits, arenas);
        metadata.validate();
        return metadata;
    }

    public void validate() {
        if (supportedKits.isEmpty()) {
            throw new IllegalStateException("Duels map must have at least one supported kit.");
        }
        for (String kitId : supportedKits) {
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(kitId);
            if (id == null || dev.frost.miniverse.minigame.core.kit.KitRegistry.get(id).isEmpty()) {
                throw new IllegalStateException("Configured kit does not exist in registry: " + kitId);
            }
        }
        if (arenas.isEmpty()) {
            throw new IllegalStateException("Duels map must have at least one arena.");
        }
    }

    private static Vec3d parseVec3d(JsonObject obj) {
        if (obj == null) return Vec3d.ZERO;
        double x = obj.has("x") ? obj.get("x").getAsDouble() : 0;
        double y = obj.has("y") ? obj.get("y").getAsDouble() : 0;
        double z = obj.has("z") ? obj.get("z").getAsDouble() : 0;
        return new Vec3d(x, y, z);
    }
}
