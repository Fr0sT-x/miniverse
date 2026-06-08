package dev.frost.miniverse.client.gui.selector.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SelectorDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final PresetManager PRESET_MANAGER = new PresetManager();
    private static final Set<Identifier> FAVORITES = new HashSet<>();
    private static boolean loaded = false;

    public static PresetManager getPresetManager() {
        ensureLoaded();
        return PRESET_MANAGER;
    }

    public static Set<Identifier> getFavorites() {
        ensureLoaded();
        return FAVORITES;
    }

    private static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("miniverse").resolve("selector_data.json");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Path file = getConfigFile();
        if (!Files.exists(file)) return;

        try {
            String content = Files.readString(file);
            JsonObject root = GSON.fromJson(content, JsonObject.class);

            if (root.has("favorites") && root.get("favorites").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("favorites")) {
                    FAVORITES.add(Identifier.of(element.getAsString()));
                }
            }

            if (root.has("presets") && root.get("presets").isJsonObject()) {
                Map<String, List<Preset>> loadedPresets = new LinkedHashMap<>();
                JsonObject presetsObj = root.getAsJsonObject("presets");
                for (Map.Entry<String, JsonElement> entry : presetsObj.entrySet()) {
                    String namespace = entry.getKey();
                    if (entry.getValue().isJsonArray()) {
                        List<Preset> list = new ArrayList<>();
                        for (JsonElement presetElement : entry.getValue().getAsJsonArray()) {
                            if (presetElement.isJsonObject()) {
                                JsonObject pObj = presetElement.getAsJsonObject();
                                String name = pObj.has("name") ? pObj.get("name").getAsString() : "Unnamed";
                                Set<Identifier> entries = new HashSet<>();
                                if (pObj.has("entries") && pObj.get("entries").isJsonArray()) {
                                    for (JsonElement idElement : pObj.getAsJsonArray("entries")) {
                                        entries.add(Identifier.of(idElement.getAsString()));
                                    }
                                }
                                list.add(new Preset(name, entries));
                            }
                        }
                        loadedPresets.put(namespace, list);
                    }
                }
                PRESET_MANAGER.load(loadedPresets);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        ensureLoaded();
        try {
            JsonObject root = new JsonObject();
            
            JsonArray favArray = new JsonArray();
            for (Identifier fav : FAVORITES) {
                favArray.add(fav.toString());
            }
            root.add("favorites", favArray);

            JsonObject presetsObj = new JsonObject();
            for (Map.Entry<String, List<Preset>> entry : PRESET_MANAGER.getAll().entrySet()) {
                JsonArray presetArray = new JsonArray();
                for (Preset p : entry.getValue()) {
                    JsonObject pObj = new JsonObject();
                    pObj.addProperty("name", p.name());
                    JsonArray entriesArray = new JsonArray();
                    for (Identifier id : p.entries()) {
                        entriesArray.add(id.toString());
                    }
                    pObj.add("entries", entriesArray);
                    presetArray.add(pObj);
                }
                presetsObj.add(entry.getKey(), presetArray);
            }
            root.add("presets", presetsObj);

            Path file = getConfigFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
