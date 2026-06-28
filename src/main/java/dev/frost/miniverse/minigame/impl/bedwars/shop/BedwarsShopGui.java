package dev.frost.miniverse.minigame.impl.bedwars.shop;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.ArrayList;

public class BedwarsShopGui {
    
    public static void open(ServerPlayerEntity player, BedwarsShopManager manager) {
        openCategory(player, manager, BedwarsShopCategory.BLOCKS);
    }

    public static void openCategory(ServerPlayerEntity player, BedwarsShopManager manager, BedwarsShopCategory activeCategory) {
        SimpleInventory inventory = new SimpleInventory(54);
        
        // Setup Categories
        ItemStack quickBuyIcon = new ItemStack(Items.NETHER_STAR);
        quickBuyIcon.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Quick Buy").formatted(activeCategory == null ? Formatting.GREEN : Formatting.YELLOW));
        if (activeCategory == null) {
            quickBuyIcon.addEnchantment(player.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT).getEntry(net.minecraft.enchantment.Enchantments.UNBREAKING).get(), 1);
        }
        inventory.setStack(0, quickBuyIcon);
        
        BedwarsShopCategory[] categories = BedwarsShopCategory.values();
        for (int i = 0; i < categories.length; i++) {
            BedwarsShopCategory category = categories[i];
            ItemStack stack = new ItemStack(category.icon());
            
            if (category == activeCategory) {
                stack.addEnchantment(player.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT).getEntry(net.minecraft.enchantment.Enchantments.UNBREAKING).get(), 1);
            }
            
            stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(category.displayName()).formatted(category == activeCategory ? Formatting.GREEN : Formatting.YELLOW));
            inventory.setStack(i + 1, stack);
        }
        
        // Row 1-4: Items
        List<BedwarsShopItem> categoryItems = new ArrayList<>();
        if (activeCategory == null) {
            // Quick Buy items
            dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsQuickBuyService qbs = manager.getQuickBuyService();
            for (BedwarsShopItem item : qbs.load(player.getUuid())) {
                if (item != null) {
                    categoryItems.add(item);
                }
            }
        } else {
            for (BedwarsShopItem item : BedwarsShopItem.values()) {
                if (item.category() == activeCategory) {
                    categoryItems.add(item);
                }
            }
        }
        
        // Layout items in grid
        for (int i = 0; i < categoryItems.size(); i++) {
            BedwarsShopItem item = categoryItems.get(i);
            ItemStack stack = item.buildStack(player.getWorld().getRegistryManager());
            
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Cost: " + item.cost() + " " + item.currency().displayName()).formatted(item.currency().formatting()));
            stack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
            
            // Grid starts at index 19 for Quickbuy (3x7), but for regular categories we just list them sequentially from index 18
            if (activeCategory == null) {
                // Quickbuy grid is indices: 19-25, 28-34, 37-43 (21 items total)
                int row = i / 7;
                int col = i % 7;
                inventory.setStack(19 + (row * 9) + col, stack);
            } else {
                inventory.setStack(18 + i, stack);
            }
        }
        
        // Row 5: Currency info
        // Simple visualization for now
        inventory.setStack(45, buildCurrencyIcon(player, dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.IRON));
        inventory.setStack(46, buildCurrencyIcon(player, dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.GOLD));
        inventory.setStack(47, buildCurrencyIcon(player, dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.DIAMOND));
        inventory.setStack(48, buildCurrencyIcon(player, dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.EMERALD));

        ItemStack close = new ItemStack(Items.BARRIER);
        close.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Close").formatted(Formatting.RED));
        inventory.setStack(53, close);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, p) -> new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inventory, 6) {
                @Override
                public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, net.minecraft.entity.player.PlayerEntity p2) {
                    if (slotIndex >= 0 && slotIndex < 54) {
                        if (actionType == net.minecraft.screen.slot.SlotActionType.PICKUP) {
                            if (slotIndex == 0) {
                                openCategory((ServerPlayerEntity) p2, manager, null);
                            } else if (slotIndex > 0 && slotIndex <= categories.length) {
                                openCategory((ServerPlayerEntity) p2, manager, categories[slotIndex - 1]);
                            } else if (slotIndex >= 18 && slotIndex < 45) {
                                BedwarsShopItem itemToBuy = null;
                                if (activeCategory == null) {
                                    int row = (slotIndex - 18) / 9;
                                    int col = (slotIndex - 18) % 9;
                                    if (col >= 1 && col <= 7 && row < 3) {
                                        int index = row * 7 + (col - 1);
                                        if (index < categoryItems.size()) {
                                            itemToBuy = categoryItems.get(index);
                                        }
                                    }
                                } else {
                                    int index = slotIndex - 18;
                                    if (index < categoryItems.size()) {
                                        itemToBuy = categoryItems.get(index);
                                    }
                                }
                                
                                if (itemToBuy != null) {
                                    if (manager.purchase((ServerPlayerEntity) p2, itemToBuy)) {
                                        p2.sendMessage(Text.literal("Purchased " + itemToBuy.displayName()).formatted(Formatting.GREEN), false);
                                    } else {
                                        p2.sendMessage(Text.literal("Not enough " + itemToBuy.currency().displayName() + "!").formatted(Formatting.RED), false);
                                    }
                                }
                            } else if (slotIndex == 53) {
                                ((ServerPlayerEntity) p2).closeHandledScreen();
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
            Text.literal("Item Shop")
        ));
    }
    
    private static ItemStack buildCurrencyIcon(ServerPlayerEntity player, dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency currency) {
        ItemStack stack = new ItemStack(currency.item());
        int count = player.getInventory().count(currency.item());
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(currency.displayName() + ": " + count).formatted(currency.formatting()));
        return stack;
    }
}
