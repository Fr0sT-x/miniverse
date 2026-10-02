package dev.frost.miniverse.minigame.impl.ctf.economy;

import dev.frost.miniverse.minigame.core.shop.ShopCurrency;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CtfCurrency {
    public static ShopCurrency coins(CtfEconomyManager economyManager) {
        return new ShopCurrency() {
            @Override
            public ItemStack getIcon() {
                return new ItemStack(Items.SUNFLOWER);
            }

            @Override
            public Text getName() {
                return Text.literal("Coins");
            }

            @Override
            public Formatting getFormatting() {
                return Formatting.GOLD;
            }

            @Override
            public int getBalance(ServerPlayerEntity player) {
                return economyManager.getCoins(player.getUuid());
            }

            @Override
            public boolean deduct(ServerPlayerEntity player, int amount) {
                return economyManager.deductCoins(player, amount);
            }
        };
    }

    public static ShopCurrency gems(CtfEconomyManager economyManager) {
        return new ShopCurrency() {
            @Override
            public ItemStack getIcon() {
                return new ItemStack(Items.EMERALD);
            }

            @Override
            public Text getName() {
                return Text.literal("Gems");
            }

            @Override
            public Formatting getFormatting() {
                return Formatting.GREEN;
            }

            @Override
            public int getBalance(ServerPlayerEntity player) {
                return economyManager.getGems(player.getUuid());
            }

            @Override
            public boolean deduct(ServerPlayerEntity player, int amount) {
                return economyManager.deductGems(player, amount);
            }
        };
    }
}
