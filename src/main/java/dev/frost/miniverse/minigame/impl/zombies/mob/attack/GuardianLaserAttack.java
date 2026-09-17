package dev.frost.miniverse.minigame.impl.zombies.mob.attack;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class GuardianLaserAttack {
    private GuardianLaserAttack() {}

    public static void tickLaser(MobEntity mob, ServerWorld world, List<ServerPlayerEntity> survivors, int attackTimer) {
        ServerPlayerEntity target = null;
        double closestSq = 16.0 * 16.0;

        for (ServerPlayerEntity p : survivors) {
            if (!p.isSpectator() && p.isAlive()) {
                double distSq = p.squaredDistanceTo(mob);
                if (distSq < closestSq) {
                    closestSq = distSq;
                    target = p;
                }
            }
        }

        if (target != null) {
            Vec3d origin = mob.getEyePos();
            Vec3d targetPos = target.getEyePos();
            Vec3d delta = targetPos.subtract(origin);
            double dist = delta.length();
            Vec3d step = delta.normalize().multiply(0.5);
            int steps = (int) (dist / 0.5);

            Vec3d cur = origin;
            for (int i = 0; i < steps; i++) {
                cur = cur.add(step);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, cur.x, cur.y, cur.z, 1, 0.0, 0.0, 0.0, 0.0);
            }

            if (attackTimer % 20 == 0) {
                target.damage(world.getDamageSources().magic(), 3.0f);
                world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 1.0f, 1.2f);
            }
        }
    }
}
