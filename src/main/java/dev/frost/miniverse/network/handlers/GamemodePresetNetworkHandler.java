package dev.frost.miniverse.network.handlers;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.preset.GamemodePreset;
import dev.frost.miniverse.minigame.core.preset.GamemodePresetStore;
import dev.frost.miniverse.session.SessionPermissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class GamemodePresetNetworkHandler {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.SAVE_GAMEMODE_PRESET_ID, (payload, context) ->
            handleSavePreset(context.server(), context.player(), payload)
        );
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.DELETE_GAMEMODE_PRESET_ID, (payload, context) ->
            handleDeletePreset(context.server(), context.player(), payload)
        );
    }

    private static void handleSavePreset(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.SaveGamemodePresetPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "save presets")) return;
        String gameId = payload.gameId();
        String name = payload.name() != null ? payload.name().trim() : "";
        if (name.isBlank() || name.length() > 64) {
            player.sendMessage(Text.literal("Invalid preset name (must be 1-64 characters).").formatted(Formatting.RED), false);
            return;
        }

        GamemodePreset preset = new GamemodePreset(gameId, name, System.currentTimeMillis(), System.currentTimeMillis(), payload.settings());
        boolean saved = GamemodePresetStore.savePreset(preset, payload.overwrite());
        if (saved) {
            player.sendMessage(Text.literal("Preset '" + name + "' saved successfully.").formatted(Formatting.GREEN), false);
            syncPresetsToAll(server, gameId);
        } else {
            player.sendMessage(Text.literal("Failed to save preset '" + name + "'. Preset may already exist.").formatted(Formatting.RED), false);
        }
    }

    private static void handleDeletePreset(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.DeleteGamemodePresetPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "delete presets")) return;
        String gameId = payload.gameId();
        String name = payload.name() != null ? payload.name().trim() : "";
        if (name.isBlank()) {
            return;
        }

        boolean deleted = GamemodePresetStore.deletePreset(gameId, name);
        if (deleted) {
            player.sendMessage(Text.literal("Preset '" + name + "' deleted.").formatted(Formatting.YELLOW), false);
            syncPresetsToAll(server, gameId);
        } else {
            player.sendMessage(Text.literal("Preset '" + name + "' not found.").formatted(Formatting.RED), false);
        }
    }

    public static void syncPresetsToPlayer(ServerPlayerEntity player, String gameId) {
        NbtCompound wrapper = new NbtCompound();
        wrapper.put("list", GamemodePresetStore.presetsToNbt(gameId));
        ServerPlayNetworking.send(player, new NetworkConstants.SyncGamemodePresetsPayload(gameId, wrapper));
    }

    public static void syncPresetsToAll(MinecraftServer server, String gameId) {
        NbtCompound wrapper = new NbtCompound();
        wrapper.put("list", GamemodePresetStore.presetsToNbt(gameId));
        NetworkConstants.SyncGamemodePresetsPayload payload = new NetworkConstants.SyncGamemodePresetsPayload(gameId, wrapper);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
