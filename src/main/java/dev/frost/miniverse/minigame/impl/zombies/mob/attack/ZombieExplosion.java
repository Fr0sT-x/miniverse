package dev.frost.miniverse.minigame.impl.zombies.mob.attack;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.List;

public final class ZombieExplosion {
    private ZombieExplosion() {}

    public static void explode(LivingEntity source, ServerWorld world, double radius, float damage) {
        double x = source.getX();
        double y = source.getY() + 0.5;
        double z = source.getZ();

        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 1.0f);

        double radiusSq = radius * radius;
        List<ServerPlayerEntity> nearbyPlayers = world.getEntitiesByClass(
            ServerPlayerEntity.class,
            source.getBoundingBox().expand(radius),
            p -> !p.isSpectator() && p.isAlive() && p.squaredDistanceTo(x, y, z) <= radiusSq
        );

        for (ServerPlayerEntity player : nearbyPlayers) {
            player.damage(world.getDamageSources().explosion(source, null), damage);
        }
    }
}
