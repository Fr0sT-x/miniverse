package dev.frost.miniverse.map.runtime;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;
import dev.frost.miniverse.map.editor.MapMarker;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.MarkerType;
import dev.frost.miniverse.session.SessionConfigJson;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RuntimeMarkerCache {
    private static final RuntimeMarkerCache INSTANCE = new RuntimeMarkerCache();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RuntimeMarkerCache.class);

    private final Map<ChunkPos, List<MapMarker>> regionIndex = new HashMap<>();
    private final List<MapMarker> allMarkers = new ArrayList<>();
    private String currentIndexKey = "";
    private String lastFailedIndexKey = "";

    private RuntimeMarkerCache() {
    }

    public static RuntimeMarkerCache getInstance() {
        return INSTANCE;
    }

    public void tick() {
        Optional<IndexConfig> indexConfig = this.resolveIndexConfig();
        String indexKey = indexConfig.map(IndexConfig::cacheKey).orElse("");
        if (!indexKey.equals(this.currentIndexKey)) {
            this.rebuildIndex(indexConfig);
        }
    }

    public List<MapMarker> getRegionsIntersecting(ChunkPos pos) {
        return this.regionIndex.getOrDefault(pos, Collections.emptyList());
    }

    public List<MapMarker> getRegionsIntersecting(Box box) {
        List<MapMarker> result = new ArrayList<>();
        int minChunkX = (int) Math.floor(box.minX) >> 4;
        int minChunkZ = (int) Math.floor(box.minZ) >> 4;
        int maxChunkX = (int) Math.floor(box.maxX) >> 4;
        int maxChunkZ = (int) Math.floor(box.maxZ) >> 4;

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                List<MapMarker> markers = this.regionIndex.get(new ChunkPos(x, z));
                if (markers != null) {
                    for (MapMarker marker : markers) {
                        if (intersects(box, marker) && !result.contains(marker)) {
                            result.add(marker);
                        }
                    }
                }
            }
        }
        return result;
    }

    public MapMarker findMarker(String id) {
        for (MapMarker marker : this.allMarkers) {
            if (marker.id().equals(id)) return marker;
        }
        return null;
    }

    private void rebuildIndex(Optional<IndexConfig> indexConfig) {
        this.clear();

        if (indexConfig.isEmpty()) {
            String failedKey = SessionRuntimeConfig.getSessionId().orElse("<no-session>") + "|missing-config";
            if (!failedKey.equals(this.lastFailedIndexKey)) {
                this.lastFailedIndexKey = failedKey;
                LOGGER.warn("RuntimeMarkerCache: cannot rebuild index because gameId or mapId is missing from runtime config");
            }
            return;
        }

        IndexConfig config = indexConfig.get();
        String gameId = config.gameId();
        String mapId = config.mapId();
        MapEditorExtension extension = MapEditorExtensionRegistry.get(gameId).orElse(null);
        if (extension == null) {
            if (!config.cacheKey().equals(this.lastFailedIndexKey)) {
                this.lastFailedIndexKey = config.cacheKey();
                LOGGER.warn("RuntimeMarkerCache: cannot rebuild index, no marker extension for gameId='{}'", gameId);
            }
            return;
        }

        Map<String, List<MapMarker>> loadedMarkers = this.resolveEmbeddedMapConfig(gameId)
            .map(configJson -> MapEditorMarkerStore.loadAll(configJson, extension))
            .orElseGet(() -> MapEditorMarkerStore.loadAll(mapId, extension));

        for (MarkerDefinition def : extension.markers()) {
            List<MapMarker> instances = loadedMarkers.getOrDefault(def.key(), List.of());
            this.allMarkers.addAll(instances);
            if (def.type() != MarkerType.REGION) continue;

            for (MapMarker marker : instances) {
                if (marker.regions().isEmpty()) continue;
                indexRegion(marker);
            }
        }
        this.currentIndexKey = config.cacheKey();
        this.lastFailedIndexKey = "";
        LOGGER.info("RuntimeMarkerCache: indexed {} markers ({} region chunk entries) for game='{}' map='{}'", this.allMarkers.size(), this.regionIndex.size(), gameId, mapId);
    }

    private Optional<IndexConfig> resolveIndexConfig() {
        Optional<com.google.gson.JsonObject> sessionJson = SessionRuntimeConfig.getSessionJson();
        if (sessionJson.isEmpty()) {
            return Optional.empty();
        }

        com.google.gson.JsonObject root = sessionJson.get();
        String sessionId = SessionConfigJson.string(root, "sessionId", "");
        String gameId = firstNonBlank(
            SessionConfigJson.string(root, "gameId", ""),
            SessionConfigJson.string(root, "game", ""),
            System.getProperty("miniverse.session.game", "")
        );
        String mapId = firstNonBlank(
            SessionConfigJson.string(root, "map", ""),
            SessionConfigJson.string(root, "world", "")
        );

        if (mapId.isBlank() && root.has("settings") && root.get("settings").isJsonObject()) {
            com.google.gson.JsonObject settings = root.getAsJsonObject("settings");
            mapId = firstNonBlank(
                SessionConfigJson.string(settings, "mapId", ""),
                nestedString(settings, "map", "id"),
                gameId.isBlank() ? "" : nestedString(settings, gameId, "mapId"),
                gameId.isBlank() ? "" : nestedString(settings, gameId, "map", "id")
            );
        }

        if (gameId.isBlank() || mapId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new IndexConfig(sessionId, gameId, mapId));
    }

    private Optional<com.google.gson.JsonObject> resolveEmbeddedMapConfig(String gameId) {
        Optional<com.google.gson.JsonObject> sessionJson = SessionRuntimeConfig.getSessionJson();
        if (sessionJson.isEmpty() || gameId == null || gameId.isBlank()) {
            return Optional.empty();
        }

        com.google.gson.JsonObject root = sessionJson.get();
        if (!root.has("settings") || !root.get("settings").isJsonObject()) {
            return Optional.empty();
        }

        String mapConfig = nestedString(root.getAsJsonObject("settings"), gameId, "mapConfig");
        if (mapConfig.isBlank()) {
            return Optional.empty();
        }

        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(mapConfig);
            return parsed != null && parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject()) : Optional.empty();
        } catch (com.google.gson.JsonParseException | IllegalStateException ignored) {
            return Optional.empty();
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String nestedString(com.google.gson.JsonObject object, String... path) {
        com.google.gson.JsonObject current = object;
        for (int i = 0; i < path.length; i++) {
            com.google.gson.JsonElement element = current.get(path[i]);
            if (element == null || element.isJsonNull()) {
                return "";
            }
            if (i == path.length - 1) {
                return element.isJsonPrimitive() ? element.getAsString() : "";
            }
            if (!element.isJsonObject()) {
                return "";
            }
            current = element.getAsJsonObject();
        }
        return "";
    }

    private void indexRegion(MapMarker marker) {
        for (dev.frost.miniverse.map.editor.RegionPart part : marker.regions()) {
            MapPosition p1 = part.min();
            MapPosition p2 = part.max();
            
            double minX = Math.min(p1.x(), p2.x());
            double minZ = Math.min(p1.z(), p2.z());
            double maxX = Math.max(p1.x(), p2.x());
            double maxZ = Math.max(p1.z(), p2.z());

            int minChunkX = (int) Math.floor(minX) >> 4;
            int minChunkZ = (int) Math.floor(minZ) >> 4;
            int maxChunkX = (int) Math.floor(maxX) >> 4;
            int maxChunkZ = (int) Math.floor(maxZ) >> 4;

            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    List<MapMarker> chunks = this.regionIndex.computeIfAbsent(new ChunkPos(x, z), k -> new ArrayList<>());
                    if (!chunks.contains(marker)) {
                        chunks.add(marker);
                    }
                }
            }
        }
    }

    private boolean intersects(Box box, MapMarker marker) {
        for (dev.frost.miniverse.map.editor.RegionPart part : marker.regions()) {
            MapPosition p1 = part.min();
            MapPosition p2 = part.max();
            double minX = Math.min(p1.x(), p2.x());
            double minY = Math.min(p1.y(), p2.y());
            double minZ = Math.min(p1.z(), p2.z());
            double maxX = Math.max(p1.x(), p2.x()) + 1.0;
            double maxY = Math.max(p1.y(), p2.y()) + 1.0;
            double maxZ = Math.max(p1.z(), p2.z()) + 1.0;
            if (box.intersects(minX, minY, minZ, maxX, maxY, maxZ)) return true;
        }
        return false;
    }

    private void clear() {
        this.regionIndex.clear();
        this.allMarkers.clear();
        this.currentIndexKey = "";
    }

    private record IndexConfig(String sessionId, String gameId, String mapId) {
        private String cacheKey() {
            return this.sessionId + "|" + this.gameId + "|" + this.mapId;
        }
    }
}
