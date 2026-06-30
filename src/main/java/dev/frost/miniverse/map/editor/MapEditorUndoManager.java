package dev.frost.miniverse.map.editor;

import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public final class MapEditorUndoManager {
    private static final Map<String, Stack<JsonObject>> UNDO_STACKS = new HashMap<>();

    private MapEditorUndoManager() {}

    public static void push(String mapId, String gameId) {
        String key = mapId + ":" + gameId;
        JsonObject config = MapStore.readGamemodeConfig(mapId, gameId).orElseGet(JsonObject::new);
        UNDO_STACKS.computeIfAbsent(key, k -> new Stack<>()).push(config.deepCopy());
    }

    public static void undo(MinecraftServer server, ServerPlayerEntity player, String mapId, String gameId) {
        String key = mapId + ":" + gameId;
        Stack<JsonObject> stack = UNDO_STACKS.get(key);
        if (stack == null || stack.isEmpty()) {
            player.sendMessage(Text.literal("Nothing to undo.").formatted(Formatting.RED), false);
            return;
        }
        JsonObject prevConfig = stack.pop();
        try {
            MapStore.writeGamemodeConfig(mapId, gameId, prevConfig);
            dev.frost.miniverse.session.SessionListSerializer.sendSessionList(server, player);
            player.sendMessage(Text.literal("Undid last action.").formatted(Formatting.GREEN), false);
        } catch (IOException e) {
            player.sendMessage(Text.literal("Failed to undo: " + e.getMessage()).formatted(Formatting.RED), false);
        }
    }
}
