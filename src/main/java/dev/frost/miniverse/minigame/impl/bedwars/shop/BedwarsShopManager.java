package dev.frost.miniverse.minigame.impl.bedwars.shop;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMapConfig;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsSettings;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BedwarsShopManager {
    private final Map<UUID, BedwarsPlayerToolState> toolStates = new ConcurrentHashMap<>();
    private final BedwarsQuickBuyService quickBuyService = new BedwarsQuickBuyService();
    private final List<UUID> shopNpcIds = new ArrayList<>();
    
    public BedwarsShopManager(BedwarsMapConfig config, BedwarsSettings settings) {
    }

    public void initPlayers(List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            toolStates.put(player.getUuid(), new BedwarsPlayerToolState());
        }
    }

    public void spawnNpcs(ServerWorld world, List<MapPosition> locations) {
        for (MapPosition loc : locations) {
            VillagerEntity npc = new VillagerEntity(EntityType.VILLAGER, world);
            npc.setPosition(loc.x(), loc.y(), loc.z());
            npc.setCustomName(Text.literal("Item Shop"));
            npc.setCustomNameVisible(true);
            npc.setAiDisabled(true);
            npc.setInvulnerable(true);
            npc.setSilent(true);
            world.spawnEntity(npc);
            shopNpcIds.add(npc.getUuid());
        }
    }

    public boolean handleInteract(ServerPlayerEntity player, net.minecraft.entity.Entity entity) {
        if (shopNpcIds.contains(entity.getUuid())) {
            BedwarsShopGui.open(player, this);
            return true;
        }
        return false;
    }

    public boolean purchase(ServerPlayerEntity player, BedwarsShopItem item) {
        int cost = item.cost();
        net.minecraft.item.Item currencyItem = item.currency().item();
        
        if (player.getInventory().count(currencyItem) >= cost) {
            // Deduct currency
            int remainingToDeduct = cost;
            for (int i = 0; i < player.getInventory().size(); i++) {
                net.minecraft.item.ItemStack stack = player.getInventory().getStack(i);
                if (stack.getItem() == currencyItem) {
                    int amountToTake = Math.min(stack.getCount(), remainingToDeduct);
                    stack.decrement(amountToTake);
                    remainingToDeduct -= amountToTake;
                    if (remainingToDeduct <= 0) break;
                }
            }
            
            // Give item
            if (item.category() == BedwarsShopCategory.TOOLS) {
                // Handle tools upgrading
                BedwarsPlayerToolState state = getToolState(player.getUuid());
                if (item.name().startsWith("PICKAXE")) {
                    state.upgradePickaxe(state.getPickaxeTier() + 1);
                    player.getInventory().offerOrDrop(state.buildPickaxe(player.getWorld().getRegistryManager()));
                } else if (item.name().startsWith("AXE")) {
                    state.upgradeAxe(state.getAxeTier() + 1);
                    player.getInventory().offerOrDrop(state.buildAxe(player.getWorld().getRegistryManager()));
                } else {
                    player.getInventory().offerOrDrop(item.buildStack(player.getWorld().getRegistryManager()));
                }
            } else if (item.category() == BedwarsShopCategory.ARMOR) {
                BedwarsPlayerToolState state = getToolState(player.getUuid());
                if (item == BedwarsShopItem.CHAINMAIL_ARMOR) {
                    state.upgradeArmor(1);
                    player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new net.minecraft.item.ItemStack(net.minecraft.item.Items.CHAINMAIL_LEGGINGS));
                    player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new net.minecraft.item.ItemStack(net.minecraft.item.Items.CHAINMAIL_BOOTS));
                } else if (item == BedwarsShopItem.IRON_ARMOR) {
                    state.upgradeArmor(2);
                    player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_LEGGINGS));
                    player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_BOOTS));
                } else if (item == BedwarsShopItem.DIAMOND_ARMOR) {
                    state.upgradeArmor(3);
                    player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_LEGGINGS));
                    player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_BOOTS));
                }
            } else {
                player.getInventory().offerOrDrop(item.buildStack(player.getWorld().getRegistryManager()));
            }
            
            return true;
        }
        
        return false;
    }
    
    public BedwarsPlayerToolState getToolState(UUID uuid) {
        return toolStates.computeIfAbsent(uuid, k -> new BedwarsPlayerToolState());
    }

    public void clear(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            for (UUID uuid : shopNpcIds) {
                for (ServerWorld world : server.getWorlds()) {
                    net.minecraft.entity.Entity e = world.getEntity(uuid);
                    if (e != null && !e.isRemoved()) {
                        e.discard();
                    }
                }
            }
        }
        shopNpcIds.clear();
    }
}
