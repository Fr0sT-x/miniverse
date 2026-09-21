package dev.frost.miniverse.minigame.impl.bedwars.shop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dev.frost.miniverse.player.PlayerDataStore;

public final class BedwarsQuickBuyService {
    public BedwarsQuickBuyService() {
    }

    public List<@Nullable String> load(UUID playerId) {
        JsonObject data = PlayerDataStore.getProfile(playerId);
        List<@Nullable String> items = new ArrayList<>(21);
        
        if (data.has("quickbuy") && data.getAsJsonObject("quickbuy").has("bedwars")) {
            JsonArray array = data.getAsJsonObject("quickbuy").getAsJsonArray("bedwars");
            for (int i = 0; i < 21; i++) {
                if (i < array.size() && !array.get(i).isJsonNull()) {
                    items.add(array.get(i).getAsString().toLowerCase());
                } else {
                    items.add(null);
                }
            }
        } else {
            // Default layout
            items.addAll(getDefault());
        }
        
        return items;
    }

    public void save(UUID playerId, List<@Nullable String> items) {
        JsonObject data = PlayerDataStore.getProfile(playerId);
        if (!data.has("quickbuy")) {
            data.add("quickbuy", new JsonObject());
        }
        
        JsonArray array = new JsonArray();
        for (int i = 0; i < 21; i++) {
            if (i < items.size() && items.get(i) != null) {
                array.add(items.get(i).toLowerCase());
            } else {
                array.add(com.google.gson.JsonNull.INSTANCE);
            }
        }
        
        data.getAsJsonObject("quickbuy").add("bedwars", array);
        PlayerDataStore.saveProfile(playerId, data);
    }

    public static List<@Nullable String> getDefault() {
        String[] arr = new String[21];
        arr[0] = "wool";
        arr[1] = "stone_sword";
        arr[2] = "chainmail_armor";
        arr[3] = "pickaxe";
        arr[4] = "bow";
        arr[5] = "speed_potion";
        arr[6] = "tnt";
        
        arr[7] = "oak_planks";
        arr[8] = "iron_sword";
        arr[9] = "iron_armor";
        arr[10] = "shears";
        arr[11] = "arrow";
        arr[12] = "jump_potion";
        arr[13] = "water_bucket";
        
        arr[14] = "end_stone";
        arr[15] = "diamond_sword";
        arr[16] = "diamond_armor";
        arr[17] = "axe";
        arr[18] = "golden_apple";
        arr[19] = "invis_potion";
        arr[20] = "fireball";
        
        return Arrays.asList(arr);
    }
}
