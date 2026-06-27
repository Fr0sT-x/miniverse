package dev.frost.miniverse.minigame.impl.bedwars.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.player.PlayerDataStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public final class BedwarsQuickBuyService {
    public BedwarsQuickBuyService() {
    }

    public List<@Nullable BedwarsShopItem> load(UUID playerId) {
        JsonObject data = PlayerDataStore.getProfile(playerId);
        List<@Nullable BedwarsShopItem> items = new ArrayList<>(21);
        
        if (data.has("quickbuy") && data.getAsJsonObject("quickbuy").has("bedwars")) {
            JsonArray array = data.getAsJsonObject("quickbuy").getAsJsonArray("bedwars");
            for (int i = 0; i < 21; i++) {
                if (i < array.size() && !array.get(i).isJsonNull()) {
                    try {
                        String name = array.get(i).getAsString().toUpperCase();
                        items.add(BedwarsShopItem.valueOf(name));
                    } catch (IllegalArgumentException e) {
                        items.add(null);
                    }
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

    public void save(UUID playerId, List<@Nullable BedwarsShopItem> items) {
        JsonObject data = PlayerDataStore.getProfile(playerId);
        if (!data.has("quickbuy")) {
            data.add("quickbuy", new JsonObject());
        }
        
        JsonArray array = new JsonArray();
        for (int i = 0; i < 21; i++) {
            if (i < items.size() && items.get(i) != null) {
                array.add(items.get(i).name().toLowerCase());
            } else {
                array.add(com.google.gson.JsonNull.INSTANCE);
            }
        }
        
        data.getAsJsonObject("quickbuy").add("bedwars", array);
        PlayerDataStore.saveProfile(playerId, data);
    }

    public static List<@Nullable BedwarsShopItem> getDefault() {
        BedwarsShopItem[] arr = new BedwarsShopItem[21];
        arr[0] = BedwarsShopItem.WOOL;
        arr[1] = BedwarsShopItem.STONE_SWORD;
        arr[2] = BedwarsShopItem.CHAINMAIL_ARMOR;
        arr[3] = BedwarsShopItem.PICKAXE_STONE;
        arr[4] = BedwarsShopItem.BOW;
        arr[5] = BedwarsShopItem.SPEED_POTION;
        arr[6] = BedwarsShopItem.TNT;
        
        arr[7] = BedwarsShopItem.OAK_PLANKS;
        arr[8] = BedwarsShopItem.IRON_SWORD;
        arr[9] = BedwarsShopItem.IRON_ARMOR;
        arr[10] = BedwarsShopItem.SHEARS;
        arr[11] = BedwarsShopItem.ARROW;
        arr[12] = BedwarsShopItem.JUMP_POTION;
        arr[13] = BedwarsShopItem.WATER_BUCKET;
        
        arr[14] = BedwarsShopItem.END_STONE;
        arr[15] = BedwarsShopItem.DIAMOND_SWORD;
        arr[16] = BedwarsShopItem.DIAMOND_ARMOR;
        arr[17] = BedwarsShopItem.AXE_STONE;
        arr[18] = BedwarsShopItem.GOLDEN_APPLE;
        arr[19] = BedwarsShopItem.INVIS_POTION;
        arr[20] = BedwarsShopItem.FIREBALL;
        
        return Arrays.asList(arr);
    }
}
