package dev.frost.miniverse.network.handlers;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.session.SessionPermissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

public class KitNetworkHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CREATE_KIT_ID, (payload, context) -> handleCreateKit(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.RENAME_KIT_ID, (payload, context) -> handleRenameKit(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.DELETE_KIT_ID, (payload, context) -> handleDeleteKit(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.GIVE_KIT_ID, (payload, context) -> handleGiveKit(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.LOAD_KIT_INTO_INVENTORY_ID, (payload, context) -> handleLoadKit(context.server(), context.player(), payload));
    }

    private static void handleCreateKit(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.CreateKitPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "manage kits")) return;
        try {
            java.util.Set<String> categories = new java.util.HashSet<>();
            for (String c : payload.categories().split(",")) {
                if (!c.trim().isEmpty()) categories.add(c.trim());
            }
            String rawId = payload.id() == null ? "" : payload.id().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            net.minecraft.util.Identifier kitId = net.minecraft.util.Identifier.tryParse(rawId.contains(":") ? rawId : "miniverse:" + rawId);
            if (kitId == null || kitId.getPath().isBlank()) {
                player.sendMessage(Text.literal("Invalid kit id.").formatted(Formatting.RED), false);
                return;
            }
            net.minecraft.item.ItemStack[] armor = new net.minecraft.item.ItemStack[4];
            net.minecraft.item.ItemStack[] inventory = new net.minecraft.item.ItemStack[36];
            net.minecraft.item.ItemStack[] offhand = new net.minecraft.item.ItemStack[1];
            for (int i = 0; i < armor.length && i < player.getInventory().armor.size(); i++) {
                armor[i] = player.getInventory().armor.get(i).copy();
            }
            for (int i = 0; i < inventory.length && i < player.getInventory().main.size(); i++) {
                inventory[i] = player.getInventory().main.get(i).copy();
            }
            if (!player.getInventory().offHand.isEmpty()) {
                offhand[0] = player.getInventory().offHand.get(0).copy();
            }
            dev.frost.miniverse.minigame.core.kit.Kit kit = new dev.frost.miniverse.minigame.core.kit.Kit(
                kitId,
                Text.literal(payload.displayName()),
                categories,
                armor,
                inventory,
                offhand,
                java.util.List.of()
            );
            dev.frost.miniverse.minigame.core.kit.KitRegistry.register(kit);
            dev.frost.miniverse.minigame.core.kit.KitRegistry.saveCustomKit(kit, server);
            player.sendMessage(Text.literal("Created kit: " + kit.getDisplayName().getString()).formatted(Formatting.GREEN), false);
            syncKitsToAll(server);
        } catch (Exception e) {
            player.sendMessage(Text.literal("Failed to create kit: " + e.getMessage()).formatted(Formatting.RED), false);
        }
    }

    private static void handleRenameKit(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.RenameKitPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "manage kits")) return;
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(payload.kitId());
        if (id == null) return;
        dev.frost.miniverse.minigame.core.kit.KitRegistry.get(id).ifPresent(kit -> {
            dev.frost.miniverse.minigame.core.kit.Kit newKit = new dev.frost.miniverse.minigame.core.kit.Kit(
                id,
                Text.literal(payload.newName()),
                kit.getCategories(),
                kit.getArmor(),
                kit.getInventory(),
                kit.getOffhand(),
                kit.getEffects()
            );
            dev.frost.miniverse.minigame.core.kit.KitRegistry.register(newKit);
            dev.frost.miniverse.minigame.core.kit.KitRegistry.saveCustomKit(newKit, server);
            player.sendMessage(Text.literal("Renamed kit to " + payload.newName()).formatted(Formatting.GREEN), false);
            syncKitsToAll(server);
        });
    }

    private static void handleDeleteKit(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.DeleteKitPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "manage kits")) return;
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(payload.kitId());
        if (id == null) return;
        dev.frost.miniverse.minigame.core.kit.KitRegistry.delete(id);
        dev.frost.miniverse.minigame.core.kit.KitRegistry.deleteCustomKit(id);
        player.sendMessage(Text.literal("Deleted kit.").formatted(Formatting.GREEN), false);
        syncKitsToAll(server);
    }

    private static void handleGiveKit(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.GiveKitPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "manage kits")) return;
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(payload.kitId());
        if (id == null) return;
        dev.frost.miniverse.minigame.core.kit.KitRegistry.get(id).ifPresent(kit -> {
            kit.apply(player);
            player.sendMessage(Text.literal("Received kit items.").formatted(Formatting.GREEN), false);
        });
    }

    private static void handleLoadKit(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.LoadKitIntoInventoryPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "manage kits")) return;
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(payload.id());
        if (id == null) return;
        dev.frost.miniverse.minigame.core.kit.KitRegistry.get(id).ifPresent(kit -> {
            kit.apply(player);
            player.sendMessage(Text.literal("Loaded kit into your inventory. Edit items, then save overwrite.").formatted(Formatting.GREEN), false);
        });
    }

    public static void syncKitsToAll(MinecraftServer server) {
        NetworkConstants.SyncKitsPayload payload = kitsSyncPayload(server);
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (SessionPermissions.canManageSessions(online)) {
                ServerPlayNetworking.send(online, payload);
            }
        }
    }

    public static void syncKitsToPlayer(MinecraftServer server, ServerPlayerEntity player) {
        if (SessionPermissions.canManageSessions(player)) {
            ServerPlayNetworking.send(player, kitsSyncPayload(server));
        }
    }

    private static NetworkConstants.SyncKitsPayload kitsSyncPayload(MinecraftServer server) {
        com.google.gson.JsonArray kitsArray = new com.google.gson.JsonArray();
        for (dev.frost.miniverse.minigame.core.kit.Kit k : dev.frost.miniverse.minigame.core.kit.KitRegistry.getAll()) {
            kitsArray.add(k.toJson(server.getRegistryManager()));
        }
        return new NetworkConstants.SyncKitsPayload(kitsArray.toString());
    }
}
