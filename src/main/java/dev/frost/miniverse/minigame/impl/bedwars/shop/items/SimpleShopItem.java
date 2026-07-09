package dev.frost.miniverse.minigame.impl.bedwars.shop.items;

import dev.frost.miniverse.minigame.core.shop.ShopCurrency;
import dev.frost.miniverse.minigame.core.shop.ShopItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public class SimpleShopItem implements ShopItem {
    private final ItemStack result;
    private final int cost;
    private final ShopCurrency currency;
    private String customName;
    private String[] customLore;

    public SimpleShopItem(ItemStack result, int cost, ShopCurrency currency) {
        this.result = result;
        this.cost = cost;
        this.currency = currency;
    }

    @Override
    public ItemStack getIcon(ServerPlayerEntity player) {
        ItemStack stack = result.copy();
        if (customName != null) {
            stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal(customName).formatted(net.minecraft.util.Formatting.GREEN));
        }
        if (customLore != null) {
            java.util.List<net.minecraft.text.Text> lines = new java.util.ArrayList<>();
            for (String l : customLore) {
                lines.add(net.minecraft.text.Text.literal(l).formatted(net.minecraft.util.Formatting.GRAY));
            }
            stack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lines));
        }
        return stack;
    }

    @Override
    public int getCost(ServerPlayerEntity player) {
        return cost;
    }

    @Override
    public ShopCurrency getCurrency(ServerPlayerEntity player) {
        return currency;
    }

    @Override
    public boolean onPurchase(ServerPlayerEntity player) {
        player.getInventory().offerOrDrop(result.copy());
        return true;
    }
    
    public SimpleShopItem withName(String name) {
        this.customName = name;
        return this;
    }

    public SimpleShopItem withLore(String... lore) {
        this.customLore = lore;
        return this;
    }
}
