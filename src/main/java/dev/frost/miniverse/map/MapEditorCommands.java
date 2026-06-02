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

    private static JsonObject editorConfig() {
        return SessionRuntimeConfig.getSessionJson()
            .filter(json -> json.has("mapEditor") && json.get("mapEditor").isJsonObject())
            .map(json -> json.getAsJsonObject("mapEditor"))
            .orElseGet(JsonObject::new);
    }
}
