package dev.frost.miniverse.minigame.core.shop;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public interface ShopItem {
    ItemStack getIcon(ServerPlayerEntity player);
    int getCost(ServerPlayerEntity player);
    ShopCurrency getCurrency(ServerPlayerEntity player);
    
    /**
     * Called when the player attempts to purchase this item.
     * @param player The player purchasing.
     * @return true if the purchase was successful (and currency should be deducted).
     */
    boolean onPurchase(ServerPlayerEntity player);
}
