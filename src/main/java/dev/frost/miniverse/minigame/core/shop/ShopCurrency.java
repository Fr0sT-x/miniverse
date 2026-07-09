package dev.frost.miniverse.minigame.core.shop;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public interface ShopCurrency {
    ItemStack getIcon();
    Text getName();
    Formatting getFormatting();
    int getBalance(ServerPlayerEntity player);
    boolean deduct(ServerPlayerEntity player, int amount);

    static ShopCurrency ofItem(Item item, String name, Formatting formatting) {
        return new ShopCurrency() {
            @Override
            public ItemStack getIcon() {
                return new ItemStack(item);
            }

            @Override
            public Text getName() {
                return Text.literal(name);
            }

            @Override
            public Formatting getFormatting() {
                return formatting;
            }

            @Override
            public int getBalance(ServerPlayerEntity player) {
                return player.getInventory().count(item);
            }

            @Override
            public boolean deduct(ServerPlayerEntity player, int amount) {
                if (getBalance(player) < amount) return false;
                int remaining = amount;
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (stack.getItem() == item) {
                        int toTake = Math.min(stack.getCount(), remaining);
                        stack.decrement(toTake);
                        remaining -= toTake;
                        if (remaining <= 0) break;
                    }
                }
                return true;
            }
        };
    }
}
