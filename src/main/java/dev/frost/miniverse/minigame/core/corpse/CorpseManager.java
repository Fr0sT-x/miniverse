package dev.frost.miniverse.minigame.core.corpse;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import dev.frost.miniverse.minigame.core.FrameworkModule;

public class CorpseManager implements FrameworkModule {
    private final List<java.util.UUID> corpseIds = new ArrayList<>();

    public void spawnCorpse(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos();

        // Basic representation of a corpse
        ArmorStandEntity corpse = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        corpse.setPosition(pos.x, pos.y, pos.z);
        corpse.setCustomName(player.getName());
        corpse.setCustomNameVisible(true);
        corpse.setNoGravity(true);
        corpse.setInvisible(false);
        
        // Setup lying down visually (in a full implementation, you might spawn a sleeping villager or a dummy player)
        corpse.setPitch(90f);

        world.spawnEntity(corpse);
        corpseIds.add(corpse.getUuid());
    }

    @Override
    public void cleanup(MinecraftServer server) {
        for (java.util.UUID uuid : corpseIds) {
            for (ServerWorld world : server.getWorlds()) {
                net.minecraft.entity.Entity e = world.getEntity(uuid);
                if (e != null && !e.isRemoved()) {
                    e.discard();
                }
            }
        }
        corpseIds.clear();
    }

    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (java.util.UUID uuid : corpseIds) {
            array.add(uuid.toString());
        }
        json.add("corpseIds", array);
        return json;
    }

    public void loadRuntimeState(com.google.gson.JsonObject json) {
        corpseIds.clear();
        if (json.has("corpseIds")) {
            for (com.google.gson.JsonElement elem : json.getAsJsonArray("corpseIds")) {
                corpseIds.add(java.util.UUID.fromString(elem.getAsString()));
            }
        }
    }
}
