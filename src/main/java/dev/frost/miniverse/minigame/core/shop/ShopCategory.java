package dev.frost.miniverse.minigame.core.shop;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public interface ShopCategory {
    Text getName();
    ItemStack getIcon();
    
    /**
     * Returns the list of items available in this category for the given player.
     * @param player The player viewing the category.
     * @return List of ShopItems.
     */
    List<ShopItem> getItems(ServerPlayerEntity player);
}
