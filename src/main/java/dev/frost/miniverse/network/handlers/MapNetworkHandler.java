package dev.frost.miniverse.network.handlers;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.session.SessionManager;
import dev.frost.miniverse.session.SessionPermissions;
import dev.frost.miniverse.session.SessionListSerializer;
import dev.frost.miniverse.session.PlayerTransferService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class MapNetworkHandler {
    
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CREATE_VOID_MAP_ID, (payload, context) -> handleCreateVoidMap(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.EDIT_MAP_ID, (payload, context) -> handleEditMap(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.RENAME_MAP_ID, (payload, context) -> handleRenameMap(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.DELETE_MAP_ID, (payload, context) -> handleDeleteMap(context.server(), context.player(), payload));
    }

    private static void handleCreateVoidMap(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.CreateVoidMapPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "create maps")) {
            return;
        }
        String mapName = payload.mapName() == null ? "" : payload.mapName().trim();
        if (mapName.isBlank()) {
            player.sendMessage(Text.literal("Enter a map name first.").formatted(Formatting.RED), false);
            return;
        }
        player.sendMessage(Text.literal("Creating void map editor for " + mapName + "..."), false);
        SessionManager.getInstance().launchMapEditorAsync(mapName, player).whenComplete((result, error) -> server.execute(() -> {
            if (error != null) {
                player.sendMessage(Text.literal("Failed to launch map editor: " + error.getMessage()).formatted(Formatting.RED), false);
                SessionListSerializer.sendSessionList(server, player);
                return;
            }

            new PlayerTransferService().transferToMapEditor(player, result.mapId(), result.port());
            player.sendMessage(Text.literal("Map editor launched for " + result.mapId() + ". Use /miniverse_map_save in the editor server to save the template."), false);
            SessionListSerializer.sendSessionList(server, player);
        }));
    }

    private static void handleRenameMap(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.RenameMapPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "rename maps")) {
            return;
        }
        if (dev.frost.miniverse.map.MapStore.rename(payload.mapId(), payload.newName())) {
            player.sendMessage(Text.literal("Renamed map.").formatted(Formatting.GREEN), false);
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                if (SessionPermissions.canManageSessions(online)) SessionListSerializer.sendSessionList(server, online);
            }
        } else {
            player.sendMessage(Text.literal("Failed to rename map.").formatted(Formatting.RED), false);
        }
    }

    private static void handleDeleteMap(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.DeleteMapPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "delete maps")) {
            return;
        }
        if (dev.frost.miniverse.map.MapStore.delete(payload.mapId())) {
            player.sendMessage(Text.literal("Deleted map.").formatted(Formatting.GREEN), false);
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                if (SessionPermissions.canManageSessions(online)) SessionListSerializer.sendSessionList(server, online);
            }
        } else {
            player.sendMessage(Text.literal("Failed to delete map.").formatted(Formatting.RED), false);
        }
    }

    private static void handleEditMap(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.EditMapPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "edit maps")) {
            return;
        }
        String mapId = payload.mapId() == null ? "" : payload.mapId().trim();
        if (mapId.isBlank()) {
            player.sendMessage(Text.literal("Invalid map id.").formatted(Formatting.RED), false);
            return;
        }
        player.sendMessage(Text.literal("Launching map editor for " + mapId + "..."), false);
        SessionManager.getInstance().launchExistingMapEditorAsync(mapId, player).whenComplete((result, error) -> server.execute(() -> {
            if (error != null) {
                player.sendMessage(Text.literal("Failed to launch map editor: " + error.getMessage()).formatted(Formatting.RED), false);
                SessionListSerializer.sendSessionList(server, player);
                return;
            }

            new PlayerTransferService().transferToMapEditor(player, result.mapId(), result.port());
            player.sendMessage(Text.literal("Map editor launched for " + result.mapId() + ". Use /miniverse_map_save in the editor server to save changes."), false);
            SessionListSerializer.sendSessionList(server, player);
        }));
    }
}
