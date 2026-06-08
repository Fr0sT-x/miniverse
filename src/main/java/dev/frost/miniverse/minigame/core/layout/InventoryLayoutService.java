package dev.frost.miniverse.minigame.core.layout;

import com.google.gson.JsonObject;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.player.PlayerDataStore;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class InventoryLayoutService {
    private InventoryLayoutService() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.SAVE_LAYOUT_ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String gamemode = payload.gamemode();
            context.server().execute(() -> handleSaveLayout(player, gamemode));
        });

        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.RESET_LAYOUT_ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String gamemode = payload.gamemode();
            context.server().execute(() -> handleResetLayout(player, gamemode));
        });
    }

    private static void handleSaveLayout(ServerPlayerEntity player, String gamemode) {
        if (!isActiveSupportedParticipant(player, gamemode)) {
            return;
        }

        Set<String> trackedItems = InventoryLayoutFramework.getTrackedItems(gamemode);
        if (trackedItems.isEmpty()) {
            return;
        }

        Map<String, Integer> foundItems = new HashMap<>();

        // Iterate strictly hotbar slots (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String kitItemId = InventoryLayoutFramework.getKitItemId(stack);
                if (kitItemId != null && trackedItems.contains(kitItemId)) {
                    foundItems.put(kitItemId, i);
                }
            }
        }

        if (foundItems.size() < trackedItems.size()) {
            player.sendMessage(Text.literal("All tracked kit items must be placed inside the hotbar before saving.").formatted(Formatting.RED), false);
            return;
        }

        JsonObject profile = PlayerDataStore.getProfile(player.getUuid());
        JsonObject layouts = profile.has("layouts") ? profile.getAsJsonObject("layouts") : new JsonObject();
        JsonObject gamemodeLayout = new JsonObject();

        for (Map.Entry<String, Integer> entry : foundItems.entrySet()) {
            gamemodeLayout.addProperty(entry.getKey(), entry.getValue());
        }

        layouts.add(gamemode, gamemodeLayout);
        profile.add("layouts", layouts);

        PlayerDataStore.saveProfile(player.getUuid(), profile);
        player.sendMessage(Text.literal("✓ Hotbar layout saved.").formatted(Formatting.GREEN), false);
    }

    private static void handleResetLayout(ServerPlayerEntity player, String gamemode) {
        if (!isActiveSupportedParticipant(player, gamemode)) {
            return;
        }

        JsonObject profile = PlayerDataStore.getProfile(player.getUuid());
        if (profile.has("layouts")) {
            JsonObject layouts = profile.getAsJsonObject("layouts");
            if (layouts.has(gamemode)) {
                layouts.remove(gamemode);
                PlayerDataStore.saveProfile(player.getUuid(), profile);
            }
        }
        player.sendMessage(Text.literal("✓ Hotbar layout reset.").formatted(Formatting.GREEN), false);
    }

    private static boolean isActiveSupportedParticipant(ServerPlayerEntity player, String gamemode) {
        if (gamemode == null || gamemode.isBlank()) {
            return false;
        }

        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime == null || !runtime.context().participants().contains(player.getUuid())) {
            return false;
        }

        if (!(runtime.minigame() instanceof InventoryLayoutAware layoutAware)) {
            return false;
        }

        return gamemode.equals(layoutAware.inventoryLayoutGamemodeId())
            && !InventoryLayoutFramework.getTrackedItems(gamemode).isEmpty();
    }
}
