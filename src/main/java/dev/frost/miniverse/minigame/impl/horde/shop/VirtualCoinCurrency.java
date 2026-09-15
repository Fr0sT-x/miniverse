package dev.frost.miniverse.minigame.impl.horde.shop;

import dev.frost.miniverse.minigame.core.shop.ShopCurrency;
import dev.frost.miniverse.minigame.impl.horde.HordeSurvivalMinigame;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class VirtualCoinCurrency implements ShopCurrency {
    private final HordeSurvivalMinigame minigame;

    public VirtualCoinCurrency(HordeSurvivalMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.GOLD_NUGGET);
    }

    @Override
    public Text getName() {
        return Text.literal("Coins").formatted(Formatting.GOLD);
    }

    @Override
    public Formatting getFormatting() {
        return Formatting.GOLD;
    }

    @Override
    public int getBalance(ServerPlayerEntity player) {
        return minigame.getCoins(player.getUuid());
    }

    @Override
    public boolean deduct(ServerPlayerEntity player, int amount) {
        return minigame.deductCoins(player.getUuid(), amount);
    }
}
