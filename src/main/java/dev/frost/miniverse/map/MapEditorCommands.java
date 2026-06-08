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

import java.nio.file.Path;
import java.nio.file.Paths;

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

    private static JsonObject editorConfig() {
        return SessionRuntimeConfig.getSessionJson()
            .filter(json -> json.has("mapEditor") && json.get("mapEditor").isJsonObject())
            .map(json -> json.getAsJsonObject("mapEditor"))
            .orElseGet(JsonObject::new);
    }
}
