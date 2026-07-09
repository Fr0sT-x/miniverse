package dev.frost.miniverse.minigame.impl.bedwars.economy;

import dev.frost.miniverse.minigame.core.shop.ShopCurrency;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum BedwarsCurrency implements ShopCurrency {
    IRON   (Items.IRON_INGOT,  "Iron",    Formatting.WHITE),
    GOLD   (Items.GOLD_INGOT,  "Gold",    Formatting.GOLD),
    DIAMOND(Items.DIAMOND,     "Diamond", Formatting.AQUA),
    EMERALD(Items.EMERALD,     "Emerald", Formatting.GREEN);

    private final ShopCurrency delegate;
    private final Item item;

    BedwarsCurrency(Item item, String displayName, Formatting formatting) {
        this.item = item;
        this.delegate = ShopCurrency.ofItem(item, displayName, formatting);
    }

    public Item item() { return this.item; }
    
    @Override
    public ItemStack getIcon() { return delegate.getIcon(); }
    @Override
    public Text getName() { return delegate.getName(); }
    @Override
    public Formatting getFormatting() { return delegate.getFormatting(); }
    @Override
    public int getBalance(ServerPlayerEntity player) { return delegate.getBalance(player); }
    @Override
    public boolean deduct(ServerPlayerEntity player, int amount) { return delegate.deduct(player, amount); }
}
