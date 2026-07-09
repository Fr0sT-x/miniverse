package dev.frost.miniverse.minigame.impl.bedwars.shop.items;

import dev.frost.miniverse.minigame.core.shop.ShopCurrency;
import dev.frost.miniverse.minigame.core.shop.ShopItem;
import dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsPlayerToolState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;
import java.util.function.Function;

public class TieredToolItem implements ShopItem {
    public enum ToolType { PICKAXE, AXE }

    public record TierCost(int cost, ShopCurrency currency) {}

    private final ToolType toolType;
    private final List<TierCost> costs;
    private final Function<ServerPlayerEntity, BedwarsPlayerToolState> stateProvider;

    private String[] customLore;

    public TieredToolItem(ToolType toolType, List<TierCost> costs, Function<ServerPlayerEntity, BedwarsPlayerToolState> stateProvider) {
        this.toolType = toolType;
        this.costs = costs;
        this.stateProvider = stateProvider;
    }
    
    public TieredToolItem withLore(String... lore) {
        this.customLore = lore;
        return this;
    }

    private int getCurrentTier(ServerPlayerEntity player) {
        BedwarsPlayerToolState state = stateProvider.apply(player);
        return toolType == ToolType.PICKAXE ? state.getPickaxeTier() : state.getAxeTier();
    }

    private boolean isMaxed(ServerPlayerEntity player) {
        return getCurrentTier(player) >= costs.size();
    }

    @Override
    public ItemStack getIcon(ServerPlayerEntity player) {
        int currentTier = getCurrentTier(player);
        int nextTier = Math.min(currentTier + 1, costs.size());
        
        BedwarsPlayerToolState dummy = new BedwarsPlayerToolState();
        if (toolType == ToolType.PICKAXE) dummy.upgradePickaxe(nextTier);
        else dummy.upgradeAxe(nextTier);

        ItemStack stack = toolType == ToolType.PICKAXE ? 
            dummy.buildPickaxe(player.getWorld().getRegistryManager()) : 
            dummy.buildAxe(player.getWorld().getRegistryManager());
            
        if (isMaxed(player)) {
            stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Maxed Out").formatted(Formatting.RED));
        }
        if (customLore != null) {
            java.util.List<net.minecraft.text.Text> lines = new java.util.ArrayList<>();
            for (String l : customLore) {
                lines.add(net.minecraft.text.Text.literal(l).formatted(Formatting.GRAY));
            }
            stack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lines));
        }
        return stack;
    }

    @Override
    public int getCost(ServerPlayerEntity player) {
        if (isMaxed(player)) return 0;
        return costs.get(getCurrentTier(player)).cost();
    }

    @Override
    public ShopCurrency getCurrency(ServerPlayerEntity player) {
        if (isMaxed(player)) return costs.get(costs.size() - 1).currency();
        return costs.get(getCurrentTier(player)).currency();
    }

    @Override
    public boolean onPurchase(ServerPlayerEntity player) {
        if (isMaxed(player)) return false;
        
        int nextTier = getCurrentTier(player) + 1;
        BedwarsPlayerToolState state = stateProvider.apply(player);
        
        // Find existing tool and remove it
        removeOldTool(player);
        
        if (toolType == ToolType.PICKAXE) {
            state.upgradePickaxe(nextTier);
            player.getInventory().offerOrDrop(state.buildPickaxe(player.getWorld().getRegistryManager()));
        } else {
            state.upgradeAxe(nextTier);
            player.getInventory().offerOrDrop(state.buildAxe(player.getWorld().getRegistryManager()));
        }
        
        return true;
    }
    
    private void removeOldTool(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (toolType == ToolType.PICKAXE && stack.getItem() instanceof net.minecraft.item.PickaxeItem) {
                stack.setCount(0);
            } else if (toolType == ToolType.AXE && stack.getItem() instanceof net.minecraft.item.AxeItem) {
                stack.setCount(0);
            }
        }
    }
}
