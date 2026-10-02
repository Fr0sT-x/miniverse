package dev.frost.miniverse.map;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.network.TransitionTransferCoordinator;
import dev.frost.miniverse.session.BackendLaunchMode;
import dev.frost.miniverse.session.SessionConfigJson;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.frost.miniverse.map.chest.MapChestScanner;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;
import dev.frost.miniverse.map.editor.MapEditorUndoManager;
import dev.frost.miniverse.map.editor.MapMarker;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.session.SessionListSerializer;
import net.minecraft.util.math.BlockPos;

public final class MapEditorCommands {
    private MapEditorCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("miniverse_map_save")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> save(context.getSource())));
        dispatcher.register(CommandManager.literal("miniverse_map_return")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> returnToLobby(context.getSource())));
        dispatcher.register(CommandManager.literal("miniverse_map_quit")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> returnToLobby(context.getSource())));
        dispatcher.register(CommandManager.literal("miniverse_map_save_and_quit")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> saveAndQuit(context.getSource())));
        dispatcher.register(CommandManager.literal("miniverse_map_set_spawn")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> setSpawn(context.getSource())));
        dispatcher.register(CommandManager.literal("miniverse_map_thumbnail")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> captureThumbnail(context.getSource())));
        dispatcher.register(CommandManager.literal("miniverse_map_scan_chests")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> scanChests(context.getSource(), "skywars", dev.frost.miniverse.map.chest.MapChestScanner.DEFAULT_MID_RADIUS, dev.frost.miniverse.map.chest.MapChestScanner.DEFAULT_SEARCH_RADIUS))
            .then(CommandManager.literal("game")
                .then(CommandManager.argument("gameId", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(context -> scanChests(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "gameId"), dev.frost.miniverse.map.chest.MapChestScanner.DEFAULT_MID_RADIUS, dev.frost.miniverse.map.chest.MapChestScanner.DEFAULT_SEARCH_RADIUS))
                    .then(CommandManager.argument("midRadius", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                        .executes(context -> scanChests(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "gameId"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "midRadius"), dev.frost.miniverse.map.chest.MapChestScanner.DEFAULT_SEARCH_RADIUS))
                        .then(CommandManager.argument("searchRadius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(10, 2000))
                            .executes(context -> scanChests(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "gameId"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "midRadius"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "searchRadius")))
                        )
                    )
                )
            )
            .then(CommandManager.argument("midRadius", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                .executes(context -> scanChests(context.getSource(), "skywars", com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "midRadius"), dev.frost.miniverse.map.chest.MapChestScanner.DEFAULT_SEARCH_RADIUS))
                .then(CommandManager.argument("searchRadius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(10, 2000))
                    .executes(context -> scanChests(context.getSource(), "skywars", com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "midRadius"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "searchRadius")))
                )
            )
        );
    }

    private static int save(ServerCommandSource source) {
        if (SessionRuntimeConfig.getLaunchMode() != BackendLaunchMode.MAP_EDITOR) {
            source.sendError(Text.literal("This command only works inside a Miniverse map editor server."));
            return 0;
        }

        JsonObject editor = editorConfig();
        String mapId = SessionConfigJson.string(editor, "mapId", "");
        if (mapId.isBlank()) {
            source.sendError(Text.literal("Map editor config is missing mapId."));
            return 0;
        }

        try {
            source.getServer().save(false, true, true);
            Path editedWorld = Paths.get("").toAbsolutePath().resolve("world");
            MapStore.saveEditorWorld(mapId, editedWorld);
            source.sendFeedback(() -> Text.literal("Saved edited world as template for map '" + mapId + "'.").formatted(Formatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to save map template: " + e.getMessage()));
            return 0;
        }
    }

    private static int returnToLobby(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can return through transfer."));
            return 0;
        }

        // Tell the client to hide all map editor overlays immediately, before the transfer starts.
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.frost.miniverse.common.NetworkConstants.MAP_EDITOR_HIDE_ID)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.frost.miniverse.common.NetworkConstants.MapEditorHidePayload());
        }

        TransitionTransferCoordinator.transfer(
            player,
            SessionRuntimeConfig.getReturnHost(),
            SessionRuntimeConfig.getReturnPort(),
            "Returning to lobby"
        );
        return 1;
    }

    private static int saveAndQuit(ServerCommandSource source) {
        int saved = save(source);
        if (saved > 0) {
            returnToLobby(source);
        }
        return saved;
    }

    private static int setSpawn(ServerCommandSource source) {
        if (SessionRuntimeConfig.getLaunchMode() != BackendLaunchMode.MAP_EDITOR) {
            source.sendError(Text.literal("This command only works inside a Miniverse map editor server."));
            return 0;
        }
        
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can set spawn."));
            return 0;
        }

        JsonObject editor = editorConfig();
        String mapId = SessionConfigJson.string(editor, "mapId", "");
        if (mapId.isBlank()) {
            source.sendError(Text.literal("Map editor config is missing mapId."));
            return 0;
        }

        try {
            dev.frost.miniverse.map.MapDescriptor map = MapStore.find(mapId).orElseThrow(() -> new Exception("Unknown map " + mapId));
            dev.frost.miniverse.map.MapMetadata metadata = map.metadata();
            dev.frost.miniverse.map.MapPosition newSpawn = new dev.frost.miniverse.map.MapPosition(
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch()
            );
            dev.frost.miniverse.map.MapMetadata newMetadata = new dev.frost.miniverse.map.MapMetadata(
                metadata.id(), metadata.name(), metadata.description(), newSpawn, metadata.tags()
            );
            
            java.nio.file.Path metadataPath = map.folder().resolve("map.json");
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            try (java.io.Writer writer = java.nio.file.Files.newBufferedWriter(metadataPath)) {
                gson.toJson(newMetadata.toJson(), writer);
            }
            
            source.sendFeedback(() -> Text.literal("Editor spawn updated to current position.").formatted(Formatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to set spawn: " + e.getMessage()));
            return 0;
        }
    }

    private static int captureThumbnail(ServerCommandSource source) {
        if (SessionRuntimeConfig.getLaunchMode() != BackendLaunchMode.MAP_EDITOR) {
            source.sendError(Text.literal("This command only works inside a Miniverse map editor server."));
            return 0;
        }

        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can capture thumbnails."));
            return 0;
        }

        JsonObject editor = editorConfig();
        String mapId = SessionConfigJson.string(editor, "mapId", "");
        if (mapId.isBlank()) {
            source.sendError(Text.literal("Map editor config is missing mapId."));
            return 0;
        }

        try {
            dev.frost.miniverse.map.MapDescriptor map = MapStore.find(mapId).orElseThrow(() -> new Exception("Unknown map " + mapId));
            java.nio.file.Path targetPath = map.folder().resolve("thumbnail.png").toAbsolutePath();
            
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.frost.miniverse.common.NetworkConstants.CAPTURE_THUMBNAIL_ID)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.frost.miniverse.common.NetworkConstants.CaptureThumbnailPayload(targetPath.toString()));
                source.sendFeedback(() -> Text.literal("Requested client to capture thumbnail.").formatted(Formatting.GREEN), false);
                return 1;
            } else {
                source.sendError(Text.literal("Client cannot receive thumbnail capture request. Ensure mod is installed on client."));
                return 0;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to trigger thumbnail capture: " + e.getMessage()));
            return 0;
        }
    }

    private static int scanChests(ServerCommandSource source, String gameId, double midRadius, int searchRadius) {
        if (SessionRuntimeConfig.getLaunchMode() != BackendLaunchMode.MAP_EDITOR) {
            source.sendError(Text.literal("This command only works inside a Miniverse map editor server."));
            return 0;
        }

        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can run chest scanning."));
            return 0;
        }

        JsonObject editor = editorConfig();
        String mapId = SessionConfigJson.string(editor, "mapId", "");
        if (mapId.isBlank()) {
            source.sendError(Text.literal("Map editor config is missing mapId."));
            return 0;
        }

        Optional<MapEditorExtension> optExt = MapEditorExtensionRegistry.get(gameId);
        if (optExt.isEmpty()) {
            source.sendError(Text.literal("Unknown gamemode extension: " + gameId));
            return 0;
        }
        MapEditorExtension extension = optExt.get();

        Optional<MarkerDefinition> islandDef = extension.marker("island_chests");
        Optional<MarkerDefinition> midDef = extension.marker("mid_chests");
        Optional<MarkerDefinition> genericDef = extension.marker("chests");

        if (islandDef.isEmpty() && midDef.isEmpty() && genericDef.isEmpty()) {
            source.sendError(Text.literal("Gamemode '" + gameId + "' does not define chest markers (island_chests, mid_chests, or chests)."));
            return 0;
        }

        List<MapPosition> spawnPositions = new ArrayList<>();
        Optional<MarkerDefinition> spawnDef = extension.marker("spawns");
        if (spawnDef.isPresent()) {
            List<MapMarker> spawns = MapEditorMarkerStore.load(mapId, extension, spawnDef.get());
            for (MapMarker spawn : spawns) {
                if (!spawn.points().isEmpty()) {
                    spawnPositions.add(spawn.points().getFirst());
                }
            }
        }

        source.sendFeedback(() -> Text.literal("Scanning chunks for containers around " + player.getBlockPos().toShortString() + "...").formatted(Formatting.YELLOW), false);

        MapChestScanner.ScanResult result = MapChestScanner.scan(
            player.getServerWorld(),
            player.getBlockPos(),
            searchRadius,
            midRadius,
            MapChestScanner.DEFAULT_ISLAND_RADIUS,
            spawnPositions
        );

        if (result.totalFound() == 0) {
            source.sendFeedback(() -> Text.literal("No chests, trapped chests, or barrels found within " + searchRadius + " blocks.").formatted(Formatting.GOLD), false);
            return 1;
        }

        MapEditorUndoManager.push(mapId, extension.gameId());

        try {
            if (islandDef.isPresent() && midDef.isPresent()) {
                List<MapMarker> islandMarkers = MapChestScanner.toMarkers(islandDef.get(), result.islandChests());
                List<MapMarker> midMarkers = MapChestScanner.toMarkers(midDef.get(), result.midChests());

                MapEditorMarkerStore.save(mapId, extension, java.util.Map.of(islandDef.get(), islandMarkers, midDef.get(), midMarkers));

                SessionListSerializer.sendSessionList(source.getServer(), player);
                source.sendFeedback(() -> Text.literal("Chest scan complete! Marked " + result.totalFound() + " containers: " 
                    + islandMarkers.size() + " Island Chests, " + midMarkers.size() + " Mid Chests.").formatted(Formatting.GREEN), false);
            } else if (genericDef.isPresent()) {
                List<BlockPos> allPositions = new ArrayList<>(result.islandChests());
                allPositions.addAll(result.midChests());
                List<MapMarker> allMarkers = MapChestScanner.toMarkers(genericDef.get(), allPositions);

                MapEditorMarkerStore.save(mapId, extension, genericDef.get(), allMarkers);

                SessionListSerializer.sendSessionList(source.getServer(), player);
                source.sendFeedback(() -> Text.literal("Chest scan complete! Marked " + allMarkers.size() + " chests.").formatted(Formatting.GREEN), false);
            }
            return 1;
        } catch (IOException e) {
            source.sendError(Text.literal("Failed to save scanned chest markers: " + e.getMessage()));
            return 0;
        }
    }

    private static JsonObject editorConfig() {
        return SessionRuntimeConfig.getSessionJson()
            .filter(json -> json.has("mapEditor") && json.get("mapEditor").isJsonObject())
            .map(json -> json.getAsJsonObject("mapEditor"))
            .orElseGet(JsonObject::new);
    }
}
