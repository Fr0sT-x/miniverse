package dev.frost.miniverse.minigame.impl.bedwars.shop.items;

import dev.frost.miniverse.minigame.core.shop.ShopCurrency;
import dev.frost.miniverse.minigame.core.shop.ShopItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class EnchantedShopItem implements ShopItem {
    private final Item baseItem;
    private final int count;
    private final int cost;
    private final ShopCurrency currency;
    private String customName;
    private String[] customLore;
    private final Map<RegistryKey<Enchantment>, Integer> enchantments = new HashMap<>();

    public EnchantedShopItem(Item baseItem, int count, int cost, ShopCurrency currency) {
        this.baseItem = baseItem;
        this.count = count;
        this.cost = cost;
        this.currency = currency;
    }

    public EnchantedShopItem withName(String name) {
        this.customName = name;
        return this;
    }

    public EnchantedShopItem withLore(String... lore) {
        this.customLore = lore;
        return this;
    }

    public EnchantedShopItem withEnchantment(RegistryKey<Enchantment> enchantment, int level) {
        this.enchantments.put(enchantment, level);
        return this;
    }

    private ItemStack buildStack(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(baseItem, count);
        if (customName != null) {
            stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(customName).formatted(net.minecraft.util.Formatting.GREEN));
        }
        if (customLore != null) {
            java.util.List<net.minecraft.text.Text> lines = new java.util.ArrayList<>();
            for (String l : customLore) {
                lines.add(net.minecraft.text.Text.literal(l).formatted(net.minecraft.util.Formatting.GRAY));
            }
            stack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lines));
        }
        
        net.minecraft.registry.RegistryWrapper.WrapperLookup reg = player.getWorld().getRegistryManager();
        for (Map.Entry<RegistryKey<Enchantment>, Integer> entry : enchantments.entrySet()) {
            reg.getOptionalWrapper(RegistryKeys.ENCHANTMENT).flatMap(r -> r.getOptional(entry.getKey()))
                .ifPresent(e -> stack.addEnchantment(e, entry.getValue()));
        }
        
        return stack;
    }

    @Override
    public ItemStack getIcon(ServerPlayerEntity player) {
        return buildStack(player);
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
        player.getInventory().offerOrDrop(buildStack(player));
        return true;
    }
}
