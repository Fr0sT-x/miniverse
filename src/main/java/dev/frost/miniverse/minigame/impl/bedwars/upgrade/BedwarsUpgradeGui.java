package dev.frost.miniverse.minigame.impl.bedwars.upgrade;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class BedwarsUpgradeGui {
    
    public static void open(ServerPlayerEntity player, BedwarsTeamUpgradeManager upgradeManager, String teamId) {
        SimpleInventory inventory = new SimpleInventory(27);
        
        BedwarsTeamUpgrade[] upgrades = BedwarsTeamUpgrade.values();
        for (int i = 0; i < upgrades.length; i++) {
            BedwarsTeamUpgrade upgrade = upgrades[i];
            int currentTier = upgradeManager.getTier(teamId, upgrade);
            int[] costs = upgrade.tierCosts();
            boolean maxed = currentTier >= costs.length;
            
            ItemStack icon = getIconForUpgrade(upgrade);
            icon.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(upgrade.displayName()).formatted(net.minecraft.util.Formatting.GOLD));
            
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal(upgrade.description()).formatted(net.minecraft.util.Formatting.GRAY));
            lore.add(Text.empty());
                
            if (maxed) {
                lore.add(Text.literal("MAXED").formatted(net.minecraft.util.Formatting.GREEN, net.minecraft.util.Formatting.BOLD));
                icon.addEnchantment(player.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT).getEntry(net.minecraft.enchantment.Enchantments.UNBREAKING).get(), 1);
            } else {
                int cost = costs[currentTier];
                lore.add(Text.literal("Cost: " + cost + " " + upgrade.currency().name()).formatted(net.minecraft.util.Formatting.YELLOW));
                lore.add(Text.literal("Click to purchase!").formatted(net.minecraft.util.Formatting.AQUA));
            }
            icon.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
            
            inventory.setStack(10 + i, icon);
        }
        
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, p) -> new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inventory, 3) {
                @Override
                public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, net.minecraft.entity.player.PlayerEntity p2) {
                    if (slotIndex >= 0 && slotIndex < 27) {
                        if (actionType == net.minecraft.screen.slot.SlotActionType.PICKUP) {
                            if (slotIndex >= 10 && slotIndex < 10 + upgrades.length) {
                                BedwarsTeamUpgrade upgrade = upgrades[slotIndex - 10];
                                int currentTier = upgradeManager.getTier(teamId, upgrade);
                                int[] costs = upgrade.tierCosts();
                                boolean maxed = currentTier >= costs.length;
                                
                                if (!maxed) {
                                    int cost = costs[currentTier];
                                    int playerCurrencyCount = player.getInventory().count(upgrade.currency().item());
                                    if (playerCurrencyCount >= cost) {
                                        int remaining = cost;
                                        for (int i = 0; i < player.getInventory().size(); i++) {
                                            ItemStack stack = player.getInventory().getStack(i);
                                            if (stack.getItem() == upgrade.currency().item()) {
                                                int toTake = Math.min(stack.getCount(), remaining);
                                                stack.decrement(toTake);
                                                remaining -= toTake;
                                                if (remaining <= 0) break;
                                            }
                                        }
                                        
                                        upgradeManager.purchase(teamId, upgrade);
                                        player.sendMessage(Text.literal("Purchased " + upgrade.displayName() + "!").formatted(net.minecraft.util.Formatting.GREEN), false);
                                        
                                        // Re-open to refresh
                                        open((ServerPlayerEntity)p2, upgradeManager, teamId);
                                    } else {
                                        player.sendMessage(Text.literal("You don't have enough " + upgrade.currency().name() + "!").formatted(net.minecraft.util.Formatting.RED), false);
                                    }
                                }
                            }
                        }
                        this.sendContentUpdates();
                    } else if (actionType == net.minecraft.screen.slot.SlotActionType.QUICK_MOVE || actionType == net.minecraft.screen.slot.SlotActionType.SWAP) {
                        this.sendContentUpdates();
                    } else {
                        super.onSlotClick(slotIndex, button, actionType, p2);
                    }
                }
            },
            Text.literal("Team Upgrades")
        ));
    }

    private static ItemStack getIconForUpgrade(BedwarsTeamUpgrade upgrade) {
        return switch (upgrade) {
            case SHARPNESS -> new ItemStack(Items.IRON_SWORD);
            case PROTECTION -> new ItemStack(Items.IRON_CHESTPLATE);
            case FORGE -> new ItemStack(Items.FURNACE);
            case HASTE -> new ItemStack(Items.GOLDEN_PICKAXE);
            case HEAL_POOL -> new ItemStack(Items.BEACON);
            case DRAGON_BUFF -> new ItemStack(Items.DRAGON_HEAD);
        };
    }
}
