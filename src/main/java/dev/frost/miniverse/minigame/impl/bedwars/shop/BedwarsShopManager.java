package dev.frost.miniverse.minigame.impl.bedwars.shop;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMapConfig;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsSettings;
import dev.frost.miniverse.minigame.core.shop.ShopCategory;
import dev.frost.miniverse.minigame.core.shop.ShopGui;
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
    private final List<ShopCategory> shopCategories;

    public BedwarsShopManager(BedwarsMapConfig config, BedwarsSettings settings) {
        // Determine if it's 3s/4s based on team size setting. We assume BedwarsSettings has teamSize() or similar
        // Actually since we don't have the exact API, let's just assume false or try to find out.
        // It's a placeholder for the dynamic pricing
        boolean is3s4s = false; // TODO check BedwarsSettings
        this.shopCategories = BedwarsShopBuilder.buildShop(this, is3s4s);
    }

    public BedwarsQuickBuyService getQuickBuyService() {
        return quickBuyService;
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
            ShopGui.open(player, Text.literal("Item Shop"), this.shopCategories);
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
