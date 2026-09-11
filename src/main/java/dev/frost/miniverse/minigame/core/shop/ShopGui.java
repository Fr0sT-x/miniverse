package dev.frost.miniverse.minigame.core.shop;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.sound.SoundEvents;

import java.util.List;

public class ShopGui {
    
    public static void open(ServerPlayerEntity player, Text title, List<ShopCategory> categories) {
        if (categories.isEmpty()) return;
        SimpleInventory inventory = new SimpleInventory(54);
        updateInventory(inventory, player, categories, categories.get(0));
        
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, p) -> new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inventory, 6) {
                private ShopCategory activeCategory = categories.get(0);

                @Override
                public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, net.minecraft.entity.player.PlayerEntity p2) {
                    if (slotIndex >= 0 && slotIndex < 54) {
                        if (actionType == net.minecraft.screen.slot.SlotActionType.PICKUP) {
                            if (slotIndex < categories.size()) {
                                this.activeCategory = categories.get(slotIndex);
                                updateInventory(inventory, (ServerPlayerEntity) p2, categories, this.activeCategory);
                            } else if (slotIndex >= 18) {
                                List<ShopItem> categoryItems = this.activeCategory.getItems((ServerPlayerEntity) p2);
                                if (slotIndex < 18 + categoryItems.size()) {
                                    ShopItem itemToBuy = categoryItems.get(slotIndex - 18);
                                    if (itemToBuy != null) {
                                        ShopCurrency currency = itemToBuy.getCurrency((ServerPlayerEntity) p2);
                                        int cost = itemToBuy.getCost((ServerPlayerEntity) p2);
                                        
                                        if (currency.getBalance((ServerPlayerEntity) p2) >= cost) {
                                            if (itemToBuy.onPurchase((ServerPlayerEntity) p2)) {
                                                currency.deduct((ServerPlayerEntity) p2, cost);
                                                p2.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                                                updateInventory(inventory, (ServerPlayerEntity) p2, categories, this.activeCategory);
                                                this.sendContentUpdates();
                                                return;
                                            } else {
                                                p2.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                            }
                                        } else {
                                            p2.sendMessage(Text.literal("Not enough ").append(currency.getName()).append("!").formatted(Formatting.RED), false);
                                            p2.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                                        }
                                    }
                                }
                            } else if (slotIndex == 53) {
                                ((ServerPlayerEntity) p2).closeHandledScreen();
                                return;
                            }
                        }
                        this.sendContentUpdates();
                        return;
                    } else if (actionType == net.minecraft.screen.slot.SlotActionType.QUICK_MOVE || actionType == net.minecraft.screen.slot.SlotActionType.SWAP) {
                        this.sendContentUpdates();
                        return;
                    } else {
                        super.onSlotClick(slotIndex, button, actionType, p2);
                    }
                }
            },
            title
        ));
    }

    private static void updateInventory(SimpleInventory inventory, ServerPlayerEntity player, List<ShopCategory> categories, ShopCategory activeCategory) {
        inventory.clear();
        
        // Setup Categories (Row 0)
        for (int i = 0; i < Math.min(categories.size(), 9); i++) {
            ShopCategory category = categories.get(i);
            ItemStack stack = category.getIcon().copy();
            
            if (category == activeCategory) {
                stack.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            
            stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, category.getName().copy().formatted(category == activeCategory ? Formatting.GREEN : Formatting.YELLOW));
            inventory.setStack(i, stack);
        }
        
        // Row 2-4: Items
        List<ShopItem> categoryItems = activeCategory.getItems(player);
        
        for (int i = 0; i < Math.min(categoryItems.size(), 27); i++) {
            ShopItem item = categoryItems.get(i);
            if (item == null) continue;
            
            ItemStack stack = item.getIcon(player).copy();
            
            // Add lore for cost
            java.util.List<Text> lore = new java.util.ArrayList<>();
            net.minecraft.component.type.LoreComponent existingLore = stack.get(net.minecraft.component.DataComponentTypes.LORE);
            if (existingLore != null) {
                lore.addAll(existingLore.lines());
                lore.add(Text.empty()); // Add a blank line before cost
            }
            lore.add(Text.literal("Cost: " + item.getCost(player) + " ").append(item.getCurrency(player).getName()).formatted(item.getCurrency(player).getFormatting()));
            stack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
            
            inventory.setStack(18 + i, stack);
        }
        
        // Row 5: Close button
        ItemStack close = new ItemStack(Items.BARRIER);
        close.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Close").formatted(Formatting.RED));
        inventory.setStack(53, close);
    }
}
