package dev.frost.miniverse.minigame.impl.murdermystery;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CoinManager {
    private final VirtualEconomyManager economy;
    private final List<MurderMysteryMapConfig.CoinSpawnPoint> spawnPoints;
    private final List<java.util.UUID> activeCoinIds = new ArrayList<>();
    
    private int spawnTimer = 0;
    private final int spawnInterval;

    public CoinManager(VirtualEconomyManager economy, List<MurderMysteryMapConfig.CoinSpawnPoint> spawnPoints, int spawnInterval) {
        this.economy = economy;
        this.spawnPoints = spawnPoints;
        this.spawnInterval = spawnInterval;
    }

    public void tick(ServerWorld world, List<ServerPlayerEntity> activePlayers) {
        spawnTimer++;
        if (spawnTimer >= spawnInterval) {
            spawnTimer = 0;
            spawnCoin(world);
        }

        java.util.Iterator<java.util.UUID> iterator = activeCoinIds.iterator();
        while (iterator.hasNext()) {
            java.util.UUID uuid = iterator.next();
            net.minecraft.entity.Entity entity = world.getEntity(uuid);
            
            if (!(entity instanceof DisplayEntity.ItemDisplayEntity coin) || coin.isRemoved()) {
                iterator.remove();
                continue;
            }
            
            Box hitbox = coin.getBoundingBox().expand(1.5);
            for (ServerPlayerEntity player : activePlayers) {
                if (hitbox.intersects(player.getBoundingBox())) {
                    economy.addCoins(player, 1);
                    world.playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 1.0f);
                    coin.discard();
                    iterator.remove();
                    break;
                }
            }
        }
    }

    private void spawnCoin(ServerWorld world) {
        if (spawnPoints.isEmpty()) return;
        List<MurderMysteryMapConfig.CoinSpawnPoint> enabled = spawnPoints.stream()
            .filter(MurderMysteryMapConfig.CoinSpawnPoint::enabled).toList();
        if (enabled.isEmpty()) return;
        
        MurderMysteryMapConfig.CoinSpawnPoint point = enabled.get(world.random.nextInt(enabled.size()));
        Vec3d pos = new Vec3d(point.position().x(), point.position().y(), point.position().z());
        
        DisplayEntity.ItemDisplayEntity coin = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        coin.setPosition(pos.x, pos.y + 0.5, pos.z);
        coin.setItemStack(new ItemStack(Items.GOLD_NUGGET));
        
        world.spawnEntity(coin);
        activeCoinIds.add(coin.getUuid());
    }

    public void clear(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            for (java.util.UUID uuid : activeCoinIds) {
                for (ServerWorld world : server.getWorlds()) {
                    net.minecraft.entity.Entity e = world.getEntity(uuid);
                    if (e != null && !e.isRemoved()) {
                        e.discard();
                    }
                }
            }
        }
        activeCoinIds.clear();
    }

    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("spawnTimer", spawnTimer);
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (java.util.UUID uuid : activeCoinIds) {
            array.add(uuid.toString());
        }
        json.add("activeCoinIds", array);
        return json;
    }

    public void loadRuntimeState(com.google.gson.JsonObject json) {
        activeCoinIds.clear();
        if (json.has("spawnTimer")) {
            spawnTimer = json.get("spawnTimer").getAsInt();
        }
        if (json.has("activeCoinIds")) {
            for (com.google.gson.JsonElement elem : json.getAsJsonArray("activeCoinIds")) {
                activeCoinIds.add(java.util.UUID.fromString(elem.getAsString()));
            }
        }
    }
}
