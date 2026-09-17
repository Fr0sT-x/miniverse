package dev.frost.miniverse.minigame.impl.zombies.mob.attack;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class FireballAttack {
    private FireballAttack() {}

    public static class ZombieFireballEntity extends SmallFireballEntity {
        public ZombieFireballEntity(ServerWorld world, MobEntity owner, Vec3d velocity) {
            super(world, owner, velocity);
        }

        @Override
        protected boolean canHit(Entity entity) {
            if (entity instanceof MobEntity) {
                return false;
            }
            return super.canHit(entity);
        }

        @Override
        protected void onBlockHit(BlockHitResult blockHitResult) {
            this.discard();
        }
    }

    public static void shootFireball(MobEntity mob, ServerWorld world, List<ServerPlayerEntity> survivors) {
        ServerPlayerEntity target = null;
        double closestSq = 20.0 * 20.0;

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
            Vec3d velocity = targetPos.subtract(origin).normalize().multiply(0.8);

            ZombieFireballEntity fireball = new ZombieFireballEntity(world, mob, velocity);
            fireball.setPosition(origin.x, origin.y, origin.z);
            world.spawnEntity(fireball);
            world.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 1.0f);
        }
    }
}
