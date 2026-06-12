package dev.frost.miniverse.network.handlers;

import com.google.gson.JsonObject;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.session.SessionListSerializer;
import dev.frost.miniverse.session.SessionPermissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

public class DuelTypeNetworkHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CREATE_DUEL_TYPE_ID, (payload, context) -> handleCreateDuelType(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.EDIT_DUEL_TYPE_ID, (payload, context) -> handleEditDuelType(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.DELETE_DUEL_TYPE_ID, (payload, context) -> handleDeleteDuelType(context.server(), context.player(), payload));
    }

    private static void handleCreateDuelType(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.CreateDuelTypePayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "create duel types")) return;
        String id = payload.id() == null ? "" : payload.id().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("_+", "_");
        String name = payload.name() == null ? "" : payload.name().trim();
        if (id.isBlank() || name.isBlank()) {
            player.sendMessage(Text.literal("Duel type name/id cannot be blank.").formatted(Formatting.RED), false);
            return;
        }
        dev.frost.miniverse.minigame.impl.duels.DuelType newType = new dev.frost.miniverse.minigame.impl.duels.DuelType(
            id,
            name,
            payload.knockbackOnly(),
            payload.allowBuilding(),
            payload.allowBreaking(),
            payload.allowHunger(),
            payload.naturalRegen()
        );
        dev.frost.miniverse.minigame.impl.duels.DuelTypeRegistry.register(newType);
        dev.frost.miniverse.minigame.impl.duels.DuelTypeRegistry.save();
        SessionListSerializer.sendSessionList(server, player);
    }

    private static void handleEditDuelType(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.EditDuelTypePayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "edit duel types")) return;
        String id = payload.id();
        if (dev.frost.miniverse.minigame.impl.duels.DuelTypeRegistry.get(id).isEmpty()) return;
        String name = payload.name() == null ? "" : payload.name().trim();
        if (name.isBlank()) {
            player.sendMessage(Text.literal("Duel type name cannot be blank.").formatted(Formatting.RED), false);
            return;
        }
        dev.frost.miniverse.minigame.impl.duels.DuelType updatedType = new dev.frost.miniverse.minigame.impl.duels.DuelType(
            id,
            name,
            payload.knockbackOnly(),
            payload.allowBuilding(),
            payload.allowBreaking(),
            payload.allowHunger(),
            payload.naturalRegen()
        );
        dev.frost.miniverse.minigame.impl.duels.DuelTypeRegistry.register(updatedType);
        dev.frost.miniverse.minigame.impl.duels.DuelTypeRegistry.save();
        SessionListSerializer.sendSessionList(server, player);
    }

    private static void handleDeleteDuelType(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.DeleteDuelTypePayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "delete duel types")) return;
        String id = payload.id();
        if (dev.frost.miniverse.minigame.impl.duels.DuelTypeRegistry.get(id).isEmpty()) return;

        dev.frost.miniverse.minigame.impl.duels.DuelTypeRegistry.remove(id);
        dev.frost.miniverse.minigame.impl.duels.DuelTypeRegistry.save();

        // Clean up kits
        for (dev.frost.miniverse.minigame.core.kit.Kit kit : dev.frost.miniverse.minigame.core.kit.KitRegistry.getAll()) {
            if (kit.getCategories().contains("duel_type:" + id)) {
                java.util.Set<String> newCategories = new java.util.HashSet<>(kit.getCategories());
                newCategories.remove("duel_type:" + id);
                dev.frost.miniverse.minigame.core.kit.Kit updatedKit = new dev.frost.miniverse.minigame.core.kit.Kit(
                    kit.getId(), kit.getDisplayName(), newCategories, kit.getArmor(), kit.getInventory(), kit.getOffhand(), kit.getEffects()
                );
                dev.frost.miniverse.minigame.core.kit.KitRegistry.register(updatedKit);
                dev.frost.miniverse.minigame.core.kit.KitRegistry.saveCustomKit(updatedKit, server);
            }
        }

        // Clean up maps
        for (dev.frost.miniverse.map.MapDescriptor map : dev.frost.miniverse.map.MapStore.scan()) {
            if (map.supports("duels")) {
                dev.frost.miniverse.map.MapStore.readGamemodeConfig(map.metadata().id(), "duels").ifPresent(config -> {
                    boolean modified = false;
                    if (config.has("arenas") && config.get("arenas").isJsonArray()) {
                        com.google.gson.JsonArray arenas = config.getAsJsonArray("arenas");
                        for (com.google.gson.JsonElement element : arenas) {
                            if (element.isJsonObject() && element.getAsJsonObject().has("properties")) {
                                JsonObject properties = element.getAsJsonObject().getAsJsonObject("properties");
                                if (properties.has("supported_duel_types") && properties.get("supported_duel_types").isJsonArray()) {
                                    com.google.gson.JsonArray types = properties.getAsJsonArray("supported_duel_types");
                                    com.google.gson.JsonArray newTypes = new com.google.gson.JsonArray();
                                    for (com.google.gson.JsonElement t : types) {
                                        if (!t.getAsString().equals(id)) {
                                            newTypes.add(t);
                                        } else {
                                            modified = true;
                                        }
                                    }
                                    if (modified) {
                                        properties.add("supported_duel_types", newTypes);
                                    }
                                }
                            }
                        }
                    }
                    if (modified) {
                        try {
                            dev.frost.miniverse.map.MapStore.writeGamemodeConfig(map.metadata().id(), "duels", config);
                        } catch (java.io.IOException e) {
                            dev.frost.miniverse.Miniverse.LOGGER.error("Failed to update map config after duel type deletion", e);
                        }
                    }
                });
            }
        }

        KitNetworkHandler.syncKitsToAll(server);
        SessionListSerializer.sendSessionList(server, player);
    }
}
