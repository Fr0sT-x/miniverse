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
        Optional<MarkerDefinition> definition = extension.get().marker(definitionKey);
        if (definition.isEmpty()) {
            player.sendMessage(Text.literal("Unknown marker definition: " + definitionKey).formatted(Formatting.RED), false);
            return;
        }
        switch (type) {
            case "start_add" -> MapEditorPlacementController.start(player, mapId, extension.get(), definition.get(), string(action, "properties", "{}"));
            case "add_logical" -> addLogicalMarker(server, player, mapId, extension.get(), definition.get(), string(action, "name", "New"), string(action, "properties", "{}"));
            case "delete" -> deleteMarker(player, mapId, extension.get(), definition.get(), string(action, "markerId", ""));
            case "teleport" -> teleportToMarker(player, mapId, extension.get(), definition.get(), string(action, "markerId", ""));
            case "rename" -> renameMarker(server, player, mapId, extension.get(), definition.get(), string(action, "markerId", ""), string(action, "name", ""));
            case "update_properties" -> updateProperties(server, player, mapId, extension.get(), definition.get(), string(action, "markerId", ""), string(action, "properties", "{}"));
            default -> player.sendMessage(Text.literal("Unknown map editor action: " + type).formatted(Formatting.RED), false);
        }
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

    private static void teleportToMarker(ServerPlayerEntity player, String mapId, MapEditorExtension extension, MarkerDefinition definition, String markerId) {
        MapEditorMarkerStore.load(mapId, extension, definition).stream()
            .filter(marker -> marker.id().equals(markerId))
            .findFirst()
            .ifPresentOrElse(marker -> {
                MapPosition target = marker.points().isEmpty() ? MapPosition.of(0.0D, 100.0D, 0.0D) : marker.points().getFirst();
                player.teleport(player.getServerWorld(), target.x() + 0.5D, target.y() + 1.0D, target.z() + 0.5D, target.yaw(), target.pitch());
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
