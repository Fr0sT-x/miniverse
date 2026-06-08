package dev.frost.miniverse.minigame.core.layout;

import com.google.gson.JsonObject;
import dev.frost.miniverse.player.PlayerDataStore;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryLayoutFramework {
    private static final Map<String, Set<String>> REGISTERED_GAMEMODES = new ConcurrentHashMap<>();

    private InventoryLayoutFramework() {}

    public static void registerGamemode(String id, Set<String> trackedKitIds) {
        REGISTERED_GAMEMODES.put(id, new HashSet<>(trackedKitIds));
    }

    public static Set<String> getTrackedItems(String gamemode) {
        return REGISTERED_GAMEMODES.getOrDefault(gamemode, Set.of());
    }

    public static void tagKitItem(ItemStack stack, String kitItemId) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            nbt.putString("miniverse:kit_item_id", kitItemId);
        });
    }

    public static String getKitItemId(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null && customData.contains("miniverse:kit_item_id")) {
            return customData.copyNbt().getString("miniverse:kit_item_id");
        }
        return null;
    }

    public static void applyLayout(ServerPlayerEntity player, String gamemode, List<ItemStack> kitItems) {
        JsonObject profile = PlayerDataStore.getProfile(player.getUuid());
        JsonObject layouts = profile.has("layouts") ? profile.getAsJsonObject("layouts") : new JsonObject();
        JsonObject gamemodeLayout = layouts.has(gamemode) ? layouts.getAsJsonObject(gamemode) : new JsonObject();

        ItemStack[] hotbar = new ItemStack[9];
        List<ItemStack> unmapped = new ArrayList<>();

        for (ItemStack item : kitItems) {
            String kitItemId = getKitItemId(item);
            boolean placed = false;
            if (kitItemId != null && gamemodeLayout.has(kitItemId)) {
                int slot = gamemodeLayout.get(kitItemId).getAsInt();
                if (slot >= 0 && slot < 9 && hotbar[slot] == null) {
                    hotbar[slot] = item;
                    placed = true;
                }
            }
            if (!placed) {
                unmapped.add(item);
            }
        }

        player.getInventory().clear();

        for (int i = 0; i < 9; i++) {
            if (hotbar[i] != null) {
                player.getInventory().setStack(i, hotbar[i]);
            } else if (!unmapped.isEmpty()) {
                player.getInventory().setStack(i, unmapped.remove(0));
            }
        }

        for (ItemStack remaining : unmapped) {
            player.getInventory().insertStack(remaining);
        }
    }
}
