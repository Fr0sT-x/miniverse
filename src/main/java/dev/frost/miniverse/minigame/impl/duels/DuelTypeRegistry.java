package dev.frost.miniverse.minigame.impl.duels;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DuelTypeRegistry {
    private static final Map<String, DuelType> REGISTRY = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void register(DuelType type) {
        REGISTRY.put(type.id(), type);
    }

    public static Optional<DuelType> get(String id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    public static Collection<DuelType> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static void clear() {
        REGISTRY.clear();
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("miniverse/duels_types.json");
    }

    public static void load() {
        clear();
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try {
                String jsonStr = Files.readString(path);
                JsonObject root = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                if (root.has("types")) {
                    for (JsonElement el : root.getAsJsonArray("types")) {
                        JsonObject obj = el.getAsJsonObject();
                        String id = obj.get("id").getAsString();
                        String name = obj.get("name").getAsString();
                        boolean knockbackOnly = obj.has("knockbackOnly") && obj.get("knockbackOnly").getAsBoolean();
                        boolean allowBuilding = !obj.has("allowBuilding") || obj.get("allowBuilding").getAsBoolean();
                        boolean allowBreaking = !obj.has("allowBreaking") || obj.get("allowBreaking").getAsBoolean();
                        boolean allowHunger = !obj.has("allowHunger") || obj.get("allowHunger").getAsBoolean();
                        boolean naturalRegen = !obj.has("naturalRegen") || obj.get("naturalRegen").getAsBoolean();
                        register(new DuelType(id, name, knockbackOnly, allowBuilding, allowBreaking, allowHunger, naturalRegen));
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to load duel types: " + e.getMessage());
            }
        } else {
            // Register some defaults
            register(new DuelType("normal_pvp", "Normal PVP", false, true, true, true, true));
            register(new DuelType("spleef", "Spleef", false, false, true, false, false));
            register(new DuelType("sumo", "Sumo", true, false, false, false, false));
            save();
        }
    }

    public static void save() {
        try {
            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            
            JsonObject root = new JsonObject();
            JsonArray typesArray = new JsonArray();
            for (DuelType type : REGISTRY.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", type.id());
                obj.addProperty("name", type.name());
                obj.addProperty("knockbackOnly", type.knockbackOnly());
                obj.addProperty("allowBuilding", type.allowBuilding());
                obj.addProperty("allowBreaking", type.allowBreaking());
                obj.addProperty("allowHunger", type.allowHunger());
                obj.addProperty("naturalRegen", type.naturalRegen());
                typesArray.add(obj);
            }
            root.add("types", typesArray);
            
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            System.err.println("Failed to save duel types: " + e.getMessage());
        }
    }
}
