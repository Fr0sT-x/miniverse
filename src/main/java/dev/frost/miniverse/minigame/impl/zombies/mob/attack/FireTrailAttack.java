package dev.frost.miniverse.minigame.impl.zombies.mob.attack;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.List;

public final class FireTrailAttack {
    private FireTrailAttack() {}

    public static void tickTrail(MobEntity mob, ServerWorld world, List<ServerPlayerEntity> survivors, int tickCounter) {
        double x = mob.getX();
        double y = mob.getY();
        double z = mob.getZ();

        // Spawn flame and smoke particles at mob feet
        world.spawnParticles(ParticleTypes.FLAME, x, y + 0.1, z, 3, 0.25, 0.1, 0.25, 0.02);
        if (tickCounter % 5 == 0) {
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.2, z, 1, 0.1, 0.1, 0.1, 0.01);
        }

        // Damage survivors in close proximity
        if (tickCounter % 20 == 0) {
            double radiusSq = 2.0 * 2.0;
            for (ServerPlayerEntity p : survivors) {
                if (!p.isSpectator() && p.isAlive() && p.squaredDistanceTo(x, y, z) <= radiusSq) {
                    p.setOnFireFor(2);
                    p.damage(world.getDamageSources().inFire(), 2.0f);
                    world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_BURN, SoundCategory.PLAYERS, 0.8f, 1.0f);
                }
            }
        }
    }
}
