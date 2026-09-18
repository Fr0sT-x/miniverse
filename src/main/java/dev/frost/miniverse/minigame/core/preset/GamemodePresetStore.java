package dev.frost.miniverse.minigame.core.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.StringNbtReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class GamemodePresetStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<GamemodePreset>> CACHE = new ConcurrentHashMap<>();

    private GamemodePresetStore() {
    }

    public static Path getPresetsDirectory(String gameId) {
        return MiniversePaths.presetsRoot().resolve(sanitizeDir(gameId));
    }

    public static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "preset";
        }
        return name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String sanitizeDir(String gameId) {
        if (gameId == null || gameId.isBlank()) {
            return "unknown";
        }
        return gameId.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }

    public static synchronized List<GamemodePreset> getPresets(String gameId) {
        String safeGameId = sanitizeDir(gameId);
        List<GamemodePreset> cached = CACHE.get(safeGameId);
        if (cached != null) {
            return Collections.unmodifiableList(cached);
        }

        List<GamemodePreset> list = scanDisk(safeGameId);
        CACHE.put(safeGameId, list);
        return Collections.unmodifiableList(list);
    }

    private static List<GamemodePreset> scanDisk(String safeGameId) {
        Path dir = MiniversePaths.presetsRoot().resolve(safeGameId);
        if (!Files.isDirectory(dir)) {
            return new ArrayList<>();
        }

        List<GamemodePreset> loaded = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> readPresetFile(path).ifPresent(loaded::add));
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to scan presets directory: {}", dir, e);
        }

        loaded.sort(Comparator.comparing(GamemodePreset::name, String.CASE_INSENSITIVE_ORDER));
        return loaded;
    }

    public static Optional<GamemodePreset> getPreset(String gameId, String name) {
        return getPresets(gameId).stream()
            .filter(p -> p.name().equalsIgnoreCase(name.trim()))
            .findFirst();
    }

    public static synchronized boolean savePreset(GamemodePreset preset, boolean overwrite) {
        if (preset == null || preset.gameId() == null || preset.name() == null || preset.name().isBlank()) {
            return false;
        }

        String safeGameId = sanitizeDir(preset.gameId());
        Path dir = MiniversePaths.presetsRoot().resolve(safeGameId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to create presets directory: {}", dir, e);
            return false;
        }

        String safeFileName = sanitizeFileName(preset.name()) + ".json";
        Path filePath = dir.resolve(safeFileName);

        if (Files.exists(filePath) && !overwrite) {
            return false;
        }

        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("gameId", preset.gameId());
        json.addProperty("name", preset.name().trim());
        json.addProperty("createdAt", preset.createdAt() > 0 ? preset.createdAt() : System.currentTimeMillis());
        json.addProperty("updatedAt", System.currentTimeMillis());

        NbtCompound settings = preset.settings() != null ? preset.settings() : new NbtCompound();
        json.addProperty("settingsSnbt", settings.asString());

        JsonObject readableSettings = new JsonObject();
        for (String key : settings.getKeys()) {
            NbtElement el = settings.get(key);
            if (el != null) {
                readableSettings.addProperty(key, el.asString());
            }
        }
        json.add("settingsReadable", readableSettings);

        try {
            Files.writeString(filePath, GSON.toJson(json));
            CACHE.remove(safeGameId);
            return true;
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to write preset file: {}", filePath, e);
            return false;
        }
    }

    public static synchronized boolean deletePreset(String gameId, String name) {
        if (gameId == null || name == null || name.isBlank()) {
            return false;
        }

        String safeGameId = sanitizeDir(gameId);
        Path dir = MiniversePaths.presetsRoot().resolve(safeGameId);
        if (!Files.isDirectory(dir)) {
            return false;
        }

        String safeFileName = sanitizeFileName(name) + ".json";
        Path filePath = dir.resolve(safeFileName);

        boolean deleted = false;
        if (Files.exists(filePath)) {
            try {
                Files.delete(filePath);
                deleted = true;
            } catch (IOException e) {
                Miniverse.LOGGER.error("Failed to delete preset file: {}", filePath, e);
            }
        } else {
            // Check if there is a preset whose internal name matches
            try (var stream = Files.list(dir)) {
                for (Path p : stream.filter(f -> f.getFileName().toString().endsWith(".json")).toList()) {
                    Optional<GamemodePreset> opt = readPresetFile(p);
                    if (opt.isPresent() && opt.get().name().equalsIgnoreCase(name.trim())) {
                        Files.delete(p);
                        deleted = true;
                        break;
                    }
                }
            } catch (IOException e) {
                Miniverse.LOGGER.error("Failed to scan while deleting preset: {}", name, e);
            }
        }

        if (deleted) {
            CACHE.remove(safeGameId);
        }
        return deleted;
    }

    public static synchronized void invalidateCache() {
        CACHE.clear();
    }

    private static Optional<GamemodePreset> readPresetFile(Path file) {
        try {
            String content = Files.readString(file);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            String gameId = json.has("gameId") ? json.get("gameId").getAsString() : "";
            String name = json.has("name") ? json.get("name").getAsString() : file.getFileName().toString().replace(".json", "");
            long createdAt = json.has("createdAt") ? json.get("createdAt").getAsLong() : Files.getLastModifiedTime(file).toMillis();
            long updatedAt = json.has("updatedAt") ? json.get("updatedAt").getAsLong() : Files.getLastModifiedTime(file).toMillis();

            NbtCompound settings = new NbtCompound();
            if (json.has("settingsSnbt")) {
                try {
                    settings = StringNbtReader.parse(json.get("settingsSnbt").getAsString());
                } catch (Exception e) {
                    Miniverse.LOGGER.warn("Failed to parse settingsSnbt in preset file: {}", file, e);
                }
            } else if (json.has("settings")) {
                JsonObject settingsObj = json.getAsJsonObject("settings");
                for (Map.Entry<String, JsonElement> entry : settingsObj.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        var prim = entry.getValue().getAsJsonPrimitive();
                        if (prim.isBoolean()) {
                            settings.putBoolean(entry.getKey(), prim.getAsBoolean());
                        } else if (prim.isNumber()) {
                            // store as int or double depending on decimal
                            if (prim.getAsString().contains(".")) {
                                settings.putDouble(entry.getKey(), prim.getAsDouble());
                            } else {
                                settings.putInt(entry.getKey(), prim.getAsInt());
                            }
                        } else {
                            settings.putString(entry.getKey(), prim.getAsString());
                        }
                    }
                }
            }

            return Optional.of(new GamemodePreset(gameId, name, createdAt, updatedAt, settings));
        } catch (Exception e) {
            Miniverse.LOGGER.warn("Failed to parse preset file: {}", file, e);
            return Optional.empty();
        }
    }

    public static NbtList presetsToNbt(String gameId) {
        NbtList list = new NbtList();
        for (GamemodePreset preset : getPresets(gameId)) {
            list.add(preset.toNbt());
        }
        return list;
    }

    public static NbtCompound allPresetsToNbt() {
        NbtCompound root = new NbtCompound();
        Path presetsRoot = MiniversePaths.presetsRoot();
        if (Files.isDirectory(presetsRoot)) {
            try (var stream = Files.list(presetsRoot)) {
                stream.filter(Files::isDirectory).forEach(gameDir -> {
                    String gameId = gameDir.getFileName().toString();
                    root.put(gameId, presetsToNbt(gameId));
                });
            } catch (IOException e) {
                Miniverse.LOGGER.warn("Failed to scan presets root: {}", presetsRoot, e);
            }
        }
        return root;
    }
}
