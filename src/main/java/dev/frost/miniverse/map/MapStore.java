package dev.frost.miniverse.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniverseFileUtils;
import dev.frost.miniverse.common.MiniversePaths;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MapStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MapStore() {
    }

    public static Path mapsRoot() {
        return MiniversePaths.mapsRoot();
    }

    public static List<MapDescriptor> scan() {
        Path root = mapsRoot();
        try {
            Files.createDirectories(root);
            try (var stream = Files.list(root)) {
                return stream
                    .filter(Files::isDirectory)
                    .map(MapStore::readDescriptor)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(descriptor -> descriptor.metadata().name(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
            }
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to scan Miniverse maps at {}", root, e);
            return List.of();
        }
    }

    public static Optional<MapDescriptor> find(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return Optional.empty();
        }
        String normalized = MapMetadata.sanitizeId(mapId);
        return scan().stream().filter(map -> map.metadata().id().equalsIgnoreCase(normalized)).findFirst();
    }

    public static MapDescriptor createEmptyMap(String name) throws IOException {
        String displayName = name == null || name.isBlank() ? "Untitled Map" : name.trim();
        String id = MapMetadata.sanitizeId(displayName);
        Path folder = mapsRoot().resolve(id);
        int suffix = 2;
        while (Files.exists(folder.resolve("world"))) {
            folder = mapsRoot().resolve(id + "_" + suffix);
            suffix++;
        }
        String finalId = folder.getFileName().toString();
        Files.createDirectories(folder.resolve("gamemodes"));
        Path metadataPath = folder.resolve("map.json");
        if (!Files.exists(metadataPath)) {
            writeJson(metadataPath, MapMetadata.defaults(finalId, displayName).toJson());
        }
        return readDescriptor(folder).orElseThrow(() -> new IOException("Failed to create map structure for " + name));
    }

    public static boolean delete(String mapId) {
        Optional<MapDescriptor> map = find(mapId);
        if (map.isEmpty()) return false;
        try {
            org.apache.commons.io.FileUtils.deleteDirectory(map.get().folder().toFile());
            return true;
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to delete map " + mapId, e);
            return false;
        }
    }

    public static boolean rename(String mapId, String newName) {
        Optional<MapDescriptor> map = find(mapId);
        if (map.isEmpty() || newName == null || newName.isBlank()) return false;
        try {
            MapMetadata metadata = map.get().metadata();
            JsonObject json = metadata.toJson();
            json.addProperty("name", newName.trim());
            Files.writeString(map.get().folder().resolve("map.json"), json.toString());
            return true;
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to rename map " + mapId, e);
            return false;
        }
    }

    public static Optional<JsonObject> readGamemodeConfig(String mapId, String gameId) {
        return find(mapId).flatMap(map -> readJson(map.folder().resolve("gamemodes").resolve(normalizeGameId(gameId) + ".json")));
    }

    public static void writeGamemodeConfig(String mapId, String gameId, JsonObject config) throws IOException {
        MapDescriptor map = find(mapId).orElseThrow(() -> new IOException("Unknown map '" + mapId + "'."));
        Path path = map.folder().resolve("gamemodes").resolve(normalizeGameId(gameId) + ".json");
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(config == null ? new JsonObject() : config, writer);
        }
    }

    public static void saveEditorWorld(String mapId, Path editedWorld) throws IOException {
        MapDescriptor map = find(mapId).orElseThrow(() -> new IOException("Unknown map '" + mapId + "'."));
        if (!Files.isDirectory(editedWorld)) {
            throw new IOException("Edited world does not exist: " + editedWorld);
        }

        Path world = map.worldFolder();
        if (Files.exists(world, LinkOption.NOFOLLOW_LINKS)) {
            Path backup = map.folder().resolve("backups").resolve("world_" + System.currentTimeMillis());
            Files.createDirectories(backup.getParent());
            Files.move(world, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        copyDirectory(editedWorld, world);
    }

    public static MapValidationResult validate(String mapId, String gameId) {
        Optional<MapDescriptor> map = find(mapId);
        if (map.isEmpty()) {
            return MapValidationResult.builder().error("Unknown map '" + mapId + "'.").build();
        }
        Optional<JsonObject> config = readGamemodeConfig(mapId, gameId);
        if (config.isEmpty()) {
            return MapValidationResult.builder().error("Map is not configured for " + gameId + ".").build();
        }
        return MapGamemodeRegistry.get(gameId)
            .map(type -> type.validator().validate(map.get(), config.get()))
            .or(() -> dev.frost.miniverse.map.editor.MapEditorExtensionRegistry.get(gameId)
                .map(extension -> dev.frost.miniverse.map.editor.MapEditorMarkerStore.validate(map.get(), config.get(), extension)))
            .orElse(MapValidationResult.ok());
    }

    public static List<MapDescriptor> compatibleMaps(String gameId) {
        return scan().stream()
            .filter(map -> map.supports(gameId))
            .filter(map -> validate(map.metadata().id(), gameId).valid())
            .toList();
    }

    public static NbtList mapsToNbt() {
        NbtList maps = new NbtList();
        for (MapDescriptor descriptor : scan()) {
            NbtCompound nbt = descriptor.toNbt();
            NbtList validations = new NbtList();
            for (String gameId : descriptor.supportedGamemodes()) {
                NbtCompound entry = new NbtCompound();
                MapValidationResult result = validate(descriptor.metadata().id(), gameId);
                entry.putString("game", gameId);
                entry.putBoolean("valid", result.valid());
                NbtList errors = new NbtList();
                for (String error : result.errors()) {
                    errors.add(net.minecraft.nbt.NbtString.of(error));
                }
                entry.put("errors", errors);
                validations.add(entry);
            }
            nbt.put("validations", validations);
            maps.add(nbt);
        }
        return maps;
    }

    public static void copyTemplateWorldToRuntime(String mapId, Path runtimeWorld) throws IOException {
        MapDescriptor map = find(mapId).orElseThrow(() -> new IOException("Unknown map '" + mapId + "'."));
        if (!Files.isDirectory(map.worldFolder())) {
            throw new IOException("Map '" + mapId + "' has no world template folder.");
        }
        if (Files.exists(runtimeWorld)) {
            MiniverseFileUtils.deleteRecursively(runtimeWorld);
        }
        copyDirectory(map.worldFolder(), runtimeWorld);
    }

    private static Optional<MapDescriptor> readDescriptor(Path folder) {
        String folderId = folder.getFileName().toString();
        Optional<JsonObject> json = readJson(folder.resolve("map.json"));
        MapMetadata metadata = json.map(value -> MapMetadata.fromJson(folderId, value)).orElseGet(() -> MapMetadata.defaults(folderId, folderId));
        Path world = folder.resolve("world");
        List<String> gamemodes = supportedGamemodes(folder.resolve("gamemodes"));
        try {
            return Optional.of(new MapDescriptor(metadata, folder, world, folder.resolve("thumbnail.png"), gamemodes, latestModifiedMillis(folder)));
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to inspect map folder {}", folder, e);
            return Optional.empty();
        }
    }

    private static List<String> supportedGamemodes(Path gamemodesFolder) {
        if (!Files.isDirectory(gamemodesFolder)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try (var stream = Files.list(gamemodesFolder)) {
            for (Path path : stream.toList()) {
                String fileName = path.getFileName().toString();
                if (Files.isRegularFile(path) && fileName.endsWith(".json")) {
                    ids.add(fileName.substring(0, fileName.length() - ".json".length()).toLowerCase(java.util.Locale.ROOT));
                }
            }
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to scan map gamemode configs at {}", gamemodesFolder, e);
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    private static Optional<JsonObject> readJson(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            var parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) {
                return Optional.of(parsed.getAsJsonObject());
            }
        } catch (IOException | IllegalStateException e) {
            Miniverse.LOGGER.warn("Failed to read map JSON {}", path, e);
        }
        return Optional.empty();
    }

    private static void writeJson(Path path, JsonObject json) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(json, writer);
        }
    }

    private static String normalizeGameId(String gameId) {
        return gameId == null ? "" : gameId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static long latestModifiedMillis(Path root) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        long latest = attrs.lastModifiedTime().toMillis();
        if (!attrs.isDirectory()) {
            return latest;
        }
        try (var stream = Files.list(root)) {
            for (Path path : stream.toList()) {
                latest = Math.max(latest, latestModifiedMillis(path));
            }
        }
        return latest;
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            for (Path sourcePath : stream.toList()) {
                Path relative = source.relativize(sourcePath);
                Path targetPath = target.resolve(relative);
                if (sourcePath.getFileName().toString().equals("session.lock")) {
                    continue;
                }
                BasicFileAttributes attrs = Files.readAttributes(sourcePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else if (attrs.isRegularFile()) {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                }
            }
        }
    }
}
