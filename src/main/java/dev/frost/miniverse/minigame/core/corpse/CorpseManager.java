package dev.frost.miniverse.minigame.core.corpse;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class CorpseManager {
    private final List<ArmorStandEntity> corpses = new ArrayList<>();

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
        corpses.add(corpse);
    }

    public void clear() {
        for (ArmorStandEntity corpse : corpses) {
            if (corpse != null && !corpse.isRemoved()) {
                corpse.discard();
            }
        }
        corpses.clear();
    }
}
