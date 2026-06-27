package dev.frost.miniverse.minigame.impl.bedwars;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HologramManager {
    private final List<UUID> holograms = new ArrayList<>();

    public HologramManager() {
    }

    public ArmorStandEntity createHologram(ServerWorld world, double x, double y, double z, Text text) {
        ArmorStandEntity armorStand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        armorStand.setPosition(x, y, z);
        armorStand.setCustomName(text);
        armorStand.setCustomNameVisible(true);
        armorStand.setInvisible(true);
        armorStand.setInvulnerable(true);
        armorStand.setNoGravity(true);
        world.spawnEntity(armorStand);
        holograms.add(armorStand.getUuid());
        return armorStand;
    }

    public void clear(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            for (UUID uuid : holograms) {
                for (ServerWorld world : server.getWorlds()) {
                    net.minecraft.entity.Entity e = world.getEntity(uuid);
                    if (e != null && !e.isRemoved()) {
                        e.discard();
                    }
                }
            }
        }
        holograms.clear();
    }
}
