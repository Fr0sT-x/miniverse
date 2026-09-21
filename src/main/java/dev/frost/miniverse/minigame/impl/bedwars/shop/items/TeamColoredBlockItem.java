package dev.frost.miniverse.minigame.impl.bedwars.shop.items;

import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.shop.ShopCurrency;
import dev.frost.miniverse.minigame.core.shop.ShopItem;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public class TeamColoredBlockItem implements ShopItem {
    private final int count;
    private final int cost;
    private final ShopCurrency currency;
    private final BlockType blockType;

    public enum BlockType {
        WOOL, GLASS, TERRACOTTA
    }

    public TeamColoredBlockItem(BlockType blockType, int count, int cost, ShopCurrency currency) {
        this.blockType = blockType;
        this.count = count;
        this.cost = cost;
        this.currency = currency;
    }

    private Item getColoredItem(Formatting color) {
        if (blockType == BlockType.WOOL) {
            return switch (color) {
                case BLACK -> Items.BLACK_WOOL;
                case DARK_BLUE -> Items.BLUE_WOOL;
                case DARK_GREEN -> Items.GREEN_WOOL;
                case DARK_AQUA -> Items.CYAN_WOOL;
                case DARK_RED -> Items.RED_WOOL;
                case DARK_PURPLE -> Items.PURPLE_WOOL;
                case GOLD -> Items.ORANGE_WOOL;
                case GRAY -> Items.LIGHT_GRAY_WOOL;
                case DARK_GRAY -> Items.GRAY_WOOL;
                case BLUE -> Items.LIGHT_BLUE_WOOL;
                case GREEN -> Items.LIME_WOOL;
                case AQUA -> Items.LIGHT_BLUE_WOOL;
                case RED -> Items.RED_WOOL;
                case LIGHT_PURPLE -> Items.MAGENTA_WOOL;
                case YELLOW -> Items.YELLOW_WOOL;
                case WHITE -> Items.WHITE_WOOL;
                default -> Items.WHITE_WOOL;
            };
        } else if (blockType == BlockType.GLASS) {
            return Items.GLASS;
        } else if (blockType == BlockType.TERRACOTTA) {
            return Items.TERRACOTTA;
        }
        return Items.STONE;
    }

    private ItemStack getStackForPlayer(ServerPlayerEntity player) {
        Formatting color = Formatting.WHITE;
        AbstractMinigame active = (AbstractMinigame) MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof TeamManagerProvider tmp) {
            TeamManager tm = tmp.teamManager();
            String teamId = tm.teamId(player.getUuid());
            if (teamId != null) {
                for (dev.frost.miniverse.team.TeamSnapshot snapshot : tm.snapshots()) {
                    if (snapshot.id().equals(teamId)) {
                        color = snapshot.color();
                        break;
                    }
                }
            }
        }
        return new ItemStack(getColoredItem(color), count);
    }

    private String customName = null;
    private String[] customLore = null;

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
        player.getInventory().offerOrDrop(getStackForPlayer(player));
        return true;
    }
    
    public TeamColoredBlockItem withName(String name) {
        this.customName = name;
        return this;
    }

    public TeamColoredBlockItem withLore(String... lore) {
        this.customLore = lore;
        return this;
    }
    
    @Override
    public ItemStack getIcon(ServerPlayerEntity player) {
        ItemStack stack = getStackForPlayer(player);
        if (customName != null) {
            stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal(customName).formatted(Formatting.GREEN));
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
}
