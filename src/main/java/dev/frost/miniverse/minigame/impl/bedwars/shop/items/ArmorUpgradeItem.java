package dev.frost.miniverse.minigame.impl.bedwars.shop.items;

import dev.frost.miniverse.minigame.core.shop.ShopCurrency;
import dev.frost.miniverse.minigame.core.shop.ShopItem;
import dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsPlayerToolState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.function.Function;

public class ArmorUpgradeItem implements ShopItem {
    private final int targetTier;
    private final int cost;
    private final ShopCurrency currency;
    private final Function<ServerPlayerEntity, BedwarsPlayerToolState> stateProvider;

    public ArmorUpgradeItem(int targetTier, int cost, ShopCurrency currency, Function<ServerPlayerEntity, BedwarsPlayerToolState> stateProvider) {
        this.targetTier = targetTier;
        this.cost = cost;
        this.currency = currency;
        this.stateProvider = stateProvider;
    }

    private int getCurrentTier(ServerPlayerEntity player) {
        return stateProvider.apply(player).getArmorTier();
    }

    private Item getLeggingsItem() {
        if (targetTier == 1) return Items.CHAINMAIL_LEGGINGS;
        if (targetTier == 2) return Items.IRON_LEGGINGS;
        if (targetTier == 3) return Items.DIAMOND_LEGGINGS;
        return Items.LEATHER_LEGGINGS;
    }
    
    private Item getBootsItem() {
        if (targetTier == 1) return Items.CHAINMAIL_BOOTS;
        if (targetTier == 2) return Items.IRON_BOOTS;
        if (targetTier == 3) return Items.DIAMOND_BOOTS;
        return Items.LEATHER_BOOTS;
    }

    @Override
    public ItemStack getIcon(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(getBootsItem());
        String name = targetTier == 1 ? "Permanent Chainmail Armor" : targetTier == 2 ? "Permanent Iron Armor" : "Permanent Diamond Armor";
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.GOLD));
        
        java.util.List<Text> lore = new java.util.ArrayList<>();
        lore.add(Text.literal("Permanent armor upgrades for").formatted(Formatting.GRAY));
        lore.add(Text.literal("you!").formatted(Formatting.GRAY));
        
        stack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
        
        if (getCurrentTier(player) >= targetTier) {
            stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Already Purchased").formatted(Formatting.RED));
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
        if (getCurrentTier(player) >= targetTier) return false;
        
        BedwarsPlayerToolState state = stateProvider.apply(player);
        state.upgradeArmor(targetTier);
        
        equipArmor(player);
        return true;
    }
    
    public static void equipArmor(ServerPlayerEntity player, int tier) {
        Item leggings = Items.LEATHER_LEGGINGS;
        Item boots = Items.LEATHER_BOOTS;
        
        if (tier == 1) { leggings = Items.CHAINMAIL_LEGGINGS; boots = Items.CHAINMAIL_BOOTS; }
        else if (tier == 2) { leggings = Items.IRON_LEGGINGS; boots = Items.IRON_BOOTS; }
        else if (tier >= 3) { leggings = Items.DIAMOND_LEGGINGS; boots = Items.DIAMOND_BOOTS; }
        
        player.getInventory().armor.set(1, new ItemStack(leggings)); // index 1 is leggings
        player.getInventory().armor.set(0, new ItemStack(boots)); // index 0 is boots
    }
    
    private void equipArmor(ServerPlayerEntity player) {
        equipArmor(player, targetTier);
    }
}
