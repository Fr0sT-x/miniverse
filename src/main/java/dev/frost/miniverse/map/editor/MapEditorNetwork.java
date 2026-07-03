package dev.frost.miniverse.map.editor;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapStore;
import dev.frost.miniverse.session.BackendLaunchMode;
import dev.frost.miniverse.session.SessionConfigJson;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MapEditorNetwork {
    private MapEditorNetwork() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.MAP_EDITOR_ACTION_ID, (payload, context) ->
            context.server().execute(() -> handle(context.server(), context.player(), payload.action()))
        );
    }

    private static void handle(MinecraftServer server, ServerPlayerEntity player, NbtCompound action) {
        if (SessionRuntimeConfig.getLaunchMode() != BackendLaunchMode.MAP_EDITOR) {
            player.sendMessage(Text.literal("Map editor actions only work inside a map editor server.").formatted(Formatting.RED), false);
            return;
        }
        String mapId = currentMapId();
        if (mapId.isBlank()) {
            player.sendMessage(Text.literal("Map editor config is missing mapId.").formatted(Formatting.RED), false);
            return;
        }
        String type = string(action, "action", "");
        String gameId = string(action, "gameId", "");
        String definitionKey = string(action, "definitionKey", "");
        Optional<MapEditorExtension> extension = MapEditorExtensionRegistry.get(gameId);
        if (extension.isEmpty()) {
            player.sendMessage(Text.literal("Unknown map editor gamemode: " + gameId).formatted(Formatting.RED), false);
            return;
        }
        if (type.equals("undo")) {
            MapEditorUndoManager.undo(server, player, mapId, extension.get().gameId());
            return;
        }
        if (type.equals("delete_all")) {
            MapEditorUndoManager.push(mapId, extension.get().gameId());
            deleteAllMarkers(server, player, mapId, extension.get());
            return;
        }
        if (type.equals("add_spatial_bulk")) {
            MapEditorUndoManager.push(mapId, extension.get().gameId());
            addSpatialBulkMarker(server, player, mapId, extension.get(), action);
            return;
        }
        
        Optional<MarkerDefinition> definition = extension.get().marker(definitionKey);
        if (definition.isEmpty()) {
            player.sendMessage(Text.literal("Unknown marker definition: " + definitionKey).formatted(Formatting.RED), false);
            return;
        }
        switch (type) {
            case "start_add" -> MapEditorPlacementController.start(player, mapId, extension.get(), definition.get(), string(action, "name", ""), string(action, "properties", "{}"));
            case "teleport" -> teleportToMarker(player, mapId, extension.get(), definition.get(), string(action, "markerId", ""));
            case "add_logical", "add_spatial", "delete", "rename", "update_properties", "move_marker" -> {
                MapEditorUndoManager.push(mapId, extension.get().gameId());
                switch (type) {
                    case "add_logical" -> addLogicalMarker(server, player, mapId, extension.get(), definition.get(), string(action, "name", "New"), string(action, "properties", "{}"));
                    case "add_spatial" -> addSpatialMarker(server, player, mapId, extension.get(), definition.get(), action);
                    case "delete" -> deleteMarker(player, mapId, extension.get(), definition.get(), string(action, "markerId", ""));
                    case "rename" -> renameMarker(server, player, mapId, extension.get(), definition.get(), string(action, "markerId", ""), string(action, "name", ""));
                    case "update_properties" -> updateProperties(server, player, mapId, extension.get(), definition.get(), string(action, "markerId", ""), string(action, "properties", "{}"));
                    case "move_marker" -> moveMarker(server, player, mapId, extension.get(), definition.get(), string(action, "markerId", ""), action);
                }
            }
            default -> player.sendMessage(Text.literal("Unknown map editor action: " + type).formatted(Formatting.RED), false);
        }
    }

    private static void addSpatialMarker(MinecraftServer server, ServerPlayerEntity player, String mapId, MapEditorExtension extension, MarkerDefinition definition, NbtCompound action) {
        String name = string(action, "name", "New");
        String propertiesJson = string(action, "properties", "{}");
        String pointsJson = string(action, "points", "[]");
        String regionsJson = string(action, "regions", "[]");
        
        com.google.gson.JsonObject properties;
        List<MapPosition> points = new ArrayList<>();
        List<dev.frost.miniverse.map.editor.RegionPart> regions = new ArrayList<>();
        
        try {
            properties = com.google.gson.JsonParser.parseString(propertiesJson).getAsJsonObject();
            
            com.google.gson.JsonArray pts = com.google.gson.JsonParser.parseString(pointsJson).getAsJsonArray();
            for (com.google.gson.JsonElement e : pts) {
                com.google.gson.JsonObject o = e.getAsJsonObject();
                points.add(new MapPosition(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(), o.get("yaw").getAsFloat(), o.get("pitch").getAsFloat()));
            }
            
            com.google.gson.JsonArray rgs = com.google.gson.JsonParser.parseString(regionsJson).getAsJsonArray();
            for (com.google.gson.JsonElement e : rgs) {
                com.google.gson.JsonObject o = e.getAsJsonObject();
                com.google.gson.JsonObject min = o.getAsJsonObject("min");
                com.google.gson.JsonObject max = o.getAsJsonObject("max");
                regions.add(new dev.frost.miniverse.map.editor.RegionPart(
                    MapPosition.of(min.get("x").getAsDouble(), min.get("y").getAsDouble(), min.get("z").getAsDouble()),
                    MapPosition.of(max.get("x").getAsDouble(), max.get("y").getAsDouble(), max.get("z").getAsDouble())
                ));
            }
        } catch (Exception e) {
            player.sendMessage(Text.literal("Invalid JSON for spatial marker.").formatted(Formatting.RED), false);
            return;
        }

        List<MapMarker> markers = new ArrayList<>(MapEditorMarkerStore.load(mapId, extension, definition));
        String id = java.util.UUID.randomUUID().toString();
        MapMarker marker = new MapMarker(id, definition.key(), name, definition.type(), points, regions, properties);
        markers.add(marker);
        
        try {
            MapEditorMarkerStore.save(mapId, extension, definition, markers);
            dev.frost.miniverse.session.SessionListSerializer.sendSessionList(server, player);
        } catch (IOException e) {
            player.sendMessage(Text.literal("Failed to add spatial marker: " + e.getMessage()).formatted(Formatting.RED), false);
        }
    }

    private static void addSpatialBulkMarker(MinecraftServer server, ServerPlayerEntity player, String mapId, MapEditorExtension extension, NbtCompound action) {
        String markersJson = string(action, "markers", "[]");
        try {
            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(markersJson).getAsJsonArray();
            java.util.Map<String, List<MapMarker>> cache = new java.util.HashMap<>();
            for (com.google.gson.JsonElement el : arr) {
                com.google.gson.JsonObject obj = el.getAsJsonObject();
                String defKey = obj.get("definitionKey").getAsString();
                MarkerDefinition def = extension.marker(defKey).orElse(null);
                if (def == null) continue;
                
                com.google.gson.JsonObject properties = obj.has("properties") ? obj.getAsJsonObject("properties") : new com.google.gson.JsonObject();
                
                List<MapPosition> points = new ArrayList<>();
                if (obj.has("points")) {
                    for (com.google.gson.JsonElement pe : obj.getAsJsonArray("points")) {
                        com.google.gson.JsonObject o = pe.getAsJsonObject();
                        points.add(new MapPosition(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(), o.get("yaw").getAsFloat(), o.get("pitch").getAsFloat()));
                    }
                }
                
                List<dev.frost.miniverse.map.editor.RegionPart> regions = new ArrayList<>();
                if (obj.has("regions")) {
                    for (com.google.gson.JsonElement re : obj.getAsJsonArray("regions")) {
                        com.google.gson.JsonObject o = re.getAsJsonObject();
                        com.google.gson.JsonObject min = o.getAsJsonObject("min");
                        com.google.gson.JsonObject max = o.getAsJsonObject("max");
                        regions.add(new dev.frost.miniverse.map.editor.RegionPart(
                            MapPosition.of(min.get("x").getAsDouble(), min.get("y").getAsDouble(), min.get("z").getAsDouble()),
                            MapPosition.of(max.get("x").getAsDouble(), max.get("y").getAsDouble(), max.get("z").getAsDouble())
                        ));
                    }
                }
                
                List<MapMarker> markers = cache.computeIfAbsent(defKey, k -> new ArrayList<>(MapEditorMarkerStore.load(mapId, extension, def)));
                String name = nextBulkMarkerName(def, markers.size() + 1, obj.has("name") ? obj.get("name").getAsString() : "");
                String id = properties.has("_forceId") ? properties.remove("_forceId").getAsString() : java.util.UUID.randomUUID().toString();
                markers.add(new MapMarker(id, def.key(), name, def.type(), points, regions, properties));
            }
            
            for (String defKey : cache.keySet()) {
                MarkerDefinition def = extension.marker(defKey).orElse(null);
                if (def != null) {
                    MapEditorMarkerStore.save(mapId, extension, def, cache.get(defKey));
                }
            }
            dev.frost.miniverse.session.SessionListSerializer.sendSessionList(server, player);
        } catch (Exception e) {
            player.sendMessage(Text.literal("Failed to process bulk markers.").formatted(Formatting.RED), false);
        }
    }

    private static String nextBulkMarkerName(MarkerDefinition definition, int index, String requestedName) {
        if ("team_config".equalsIgnoreCase(definition.key()) && requestedName != null && !requestedName.isBlank()) {
            return requestedName;
        }
        if (definition.single()) {
            return definition.displayName();
        }
        return definition.displayName() + " #" + Math.max(1, index);
    }

    private static void addLogicalMarker(MinecraftServer server, ServerPlayerEntity player, String mapId, MapEditorExtension extension, MarkerDefinition definition, String name, String propertiesJson) {
        com.google.gson.JsonObject properties;
        try {
            properties = com.google.gson.JsonParser.parseString(propertiesJson).getAsJsonObject();
        } catch (Exception e) {
            player.sendMessage(Text.literal("Invalid properties JSON.").formatted(Formatting.RED), false);
            return;
        }

        List<MapMarker> markers = new ArrayList<>(MapEditorMarkerStore.load(mapId, extension, definition));
        String id = java.util.UUID.randomUUID().toString();
        MapMarker marker = new MapMarker(id, definition.key(), name, definition.type(), List.of(), List.of(), properties);
        markers.add(marker);
        
        try {
            MapEditorMarkerStore.save(mapId, extension, definition, markers);
            dev.frost.miniverse.session.SessionListSerializer.sendSessionList(server, player);
            player.sendMessage(Text.literal("Added logical marker.").formatted(Formatting.GREEN), false);
        } catch (IOException e) {
            player.sendMessage(Text.literal("Failed to add logical marker: " + e.getMessage()).formatted(Formatting.RED), false);
        }
    }

    private static void deleteMarker(ServerPlayerEntity player, String mapId, MapEditorExtension extension, MarkerDefinition definition, String markerId) {
        List<MapMarker> markers = new ArrayList<>(MapEditorMarkerStore.load(mapId, extension, definition));
        boolean removed = markers.removeIf(marker -> marker.id().equals(markerId));
        if (!removed) {
            player.sendMessage(Text.literal("Marker not found.").formatted(Formatting.RED), false);
            return;
        }
        try {
            MapEditorMarkerStore.save(mapId, extension, definition, markers);
            dev.frost.miniverse.session.SessionListSerializer.sendSessionList(player.server, player);
            player.sendMessage(Text.literal("Deleted marker.").formatted(Formatting.GREEN), false);
        } catch (IOException e) {
            player.sendMessage(Text.literal("Failed to delete marker: " + e.getMessage()).formatted(Formatting.RED), false);
        }
    }

    private static void deleteAllMarkers(MinecraftServer server, ServerPlayerEntity player, String mapId, MapEditorExtension extension) {
        try {
            for (MarkerDefinition def : extension.markers()) {
                MapEditorMarkerStore.save(mapId, extension, def, List.of());
            }
            dev.frost.miniverse.session.SessionListSerializer.sendSessionList(server, player);
            player.sendMessage(Text.literal("Deleted all markers for this map.").formatted(Formatting.GREEN), false);
        } catch (IOException e) {
            player.sendMessage(Text.literal("Failed to clear markers: " + e.getMessage()).formatted(Formatting.RED), false);
        }
    }

    private static void teleportToMarker(ServerPlayerEntity player, String mapId, MapEditorExtension extension, MarkerDefinition definition, String markerId) {
        MapEditorMarkerStore.load(mapId, extension, definition).stream()
            .filter(marker -> marker.id().equals(markerId))
            .findFirst()
            .ifPresentOrElse(marker -> {
                MapPosition target = marker.points().isEmpty() ? MapPosition.of(0.0D, 100.0D, 0.0D) : marker.points().getFirst();
                player.teleport(player.getServerWorld(), target.x(), target.y(), target.z(), target.yaw(), target.pitch());
            }, () -> player.sendMessage(Text.literal("Marker not found.").formatted(Formatting.RED), false));
    }

    private static void renameMarker(MinecraftServer server, ServerPlayerEntity player, String mapId, MapEditorExtension extension, MarkerDefinition definition, String markerId, String name) {
        if (name == null || name.isBlank()) {
            player.sendMessage(Text.literal("Marker name cannot be blank.").formatted(Formatting.RED), false);
            return;
        }
        List<MapMarker> markers = new ArrayList<>(MapEditorMarkerStore.load(mapId, extension, definition));
        for (int i = 0; i < markers.size(); i++) {
            MapMarker marker = markers.get(i);
            if (marker.id().equals(markerId)) {
                markers.set(i, new MapMarker(marker.id(), marker.definitionKey(), name.trim(), marker.type(), marker.points(), marker.regions(), marker.properties()));
                try {
                    MapEditorMarkerStore.save(mapId, extension, definition, markers);
                    dev.frost.miniverse.session.SessionListSerializer.sendSessionList(server, player);
                    player.sendMessage(Text.literal("Renamed marker.").formatted(Formatting.GREEN), false);
                } catch (IOException e) {
                    player.sendMessage(Text.literal("Failed to rename marker: " + e.getMessage()).formatted(Formatting.RED), false);
                }
                return;
            }
        }
        player.sendMessage(Text.literal("Marker not found.").formatted(Formatting.RED), false);
    }

    private static void updateProperties(MinecraftServer server, ServerPlayerEntity player, String mapId, MapEditorExtension extension, MarkerDefinition definition, String markerId, String propertiesJson) {
        com.google.gson.JsonObject properties;
        try {
            properties = com.google.gson.JsonParser.parseString(propertiesJson).getAsJsonObject();
        } catch (Exception e) {
            player.sendMessage(Text.literal("Invalid properties JSON.").formatted(Formatting.RED), false);
            return;
        }

        List<MapMarker> markers = new ArrayList<>(MapEditorMarkerStore.load(mapId, extension, definition));
        for (int i = 0; i < markers.size(); i++) {
            MapMarker marker = markers.get(i);
            if (marker.id().equals(markerId)) {
                markers.set(i, new MapMarker(marker.id(), marker.definitionKey(), marker.name(), marker.type(), marker.points(), marker.regions(), properties));
                try {
                    MapEditorMarkerStore.save(mapId, extension, definition, markers);
                    dev.frost.miniverse.session.SessionListSerializer.sendSessionList(server, player);
                    player.sendMessage(Text.literal("Updated marker properties.").formatted(Formatting.GREEN), false);
                } catch (IOException e) {
                    player.sendMessage(Text.literal("Failed to update marker properties: " + e.getMessage()).formatted(Formatting.RED), false);
                }
                return;
            }
        }
        player.sendMessage(Text.literal("Marker not found.").formatted(Formatting.RED), false);
    }

    private static void moveMarker(MinecraftServer server, ServerPlayerEntity player, String mapId, MapEditorExtension extension, MarkerDefinition definition, String markerId, NbtCompound action) {
        List<MapMarker> markers = new ArrayList<>(MapEditorMarkerStore.load(mapId, extension, definition));
        for (int i = 0; i < markers.size(); i++) {
            MapMarker marker = markers.get(i);
            if (marker.id().equals(markerId)) {
                String pointsJson = string(action, "points", "[]");
                String regionsJson = string(action, "regions", "[]");
                List<MapPosition> points = new ArrayList<>();
                List<dev.frost.miniverse.map.editor.RegionPart> regions = new ArrayList<>();
                
                try {
                    com.google.gson.JsonArray pts = com.google.gson.JsonParser.parseString(pointsJson).getAsJsonArray();
                    for (com.google.gson.JsonElement e : pts) {
                        com.google.gson.JsonObject o = e.getAsJsonObject();
                        points.add(new MapPosition(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(), o.get("yaw").getAsFloat(), o.get("pitch").getAsFloat()));
                    }
                    
                    com.google.gson.JsonArray rgs = com.google.gson.JsonParser.parseString(regionsJson).getAsJsonArray();
                    for (com.google.gson.JsonElement e : rgs) {
                        com.google.gson.JsonObject o = e.getAsJsonObject();
                        com.google.gson.JsonObject min = o.getAsJsonObject("min");
                        com.google.gson.JsonObject max = o.getAsJsonObject("max");
                        regions.add(new dev.frost.miniverse.map.editor.RegionPart(
                            MapPosition.of(min.get("x").getAsDouble(), min.get("y").getAsDouble(), min.get("z").getAsDouble()),
                            MapPosition.of(max.get("x").getAsDouble(), max.get("y").getAsDouble(), max.get("z").getAsDouble())
                        ));
                    }
                } catch (Exception e) {
                    player.sendMessage(Text.literal("Invalid JSON for spatial marker.").formatted(Formatting.RED), false);
                    return;
                }
                
                markers.set(i, new MapMarker(marker.id(), marker.definitionKey(), marker.name(), marker.type(), points, regions, marker.properties()));
                try {
                    MapEditorMarkerStore.save(mapId, extension, definition, markers);
                    dev.frost.miniverse.session.SessionListSerializer.sendSessionList(server, player);
                    player.sendMessage(Text.literal("Moved marker.").formatted(Formatting.GREEN), false);
                } catch (IOException e) {
                    player.sendMessage(Text.literal("Failed to move marker: " + e.getMessage()).formatted(Formatting.RED), false);
                }
                return;
            }
        }
        player.sendMessage(Text.literal("Marker not found.").formatted(Formatting.RED), false);
    }

    public static String currentMapId() {
        return SessionRuntimeConfig.getSessionJson()
            .filter(json -> json.has("mapEditor") && json.get("mapEditor").isJsonObject())
            .map(json -> json.getAsJsonObject("mapEditor"))
            .map(editor -> SessionConfigJson.string(editor, "mapId", ""))
            .orElse("");
    }

    private static String string(NbtCompound nbt, String key, String fallback) {
        return nbt != null && nbt.contains(key, NbtElement.STRING_TYPE) ? nbt.getString(key) : fallback;
    }
}
