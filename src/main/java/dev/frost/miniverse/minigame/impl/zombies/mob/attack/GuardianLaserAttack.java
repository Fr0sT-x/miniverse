package dev.frost.miniverse.minigame.impl.zombies.mob.attack;

import dev.frost.miniverse.minigame.impl.zombies.mob.ZombieEntityManager;
import dev.frost.miniverse.mixin.GuardianEntityAccessor;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

public final class GuardianLaserAttack {
    private static final double MAX_RANGE = 15.0;
    private static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;
    private static final int CHARGE_TICKS = 60; // 3.0 seconds to charge up
    private static final int COOLDOWN_TICKS = 80; // 4.0 seconds cooldown between attacks

    public static void tickLaser(
        MobEntity mob,
        ZombieEntityManager.ActiveMob active,
        ServerWorld world,
        List<ServerPlayerEntity> survivors
    ) {
        tickLaser(mob, active, world, survivors, 4.0f);
    }

    public static void tickLaser(
        MobEntity mob,
        ZombieEntityManager.ActiveMob active,
        ServerWorld world,
        List<ServerPlayerEntity> survivors,
        float damage
    ) {
        GuardianEntity guardian = active.laserGuardian;
        if (guardian == null || !guardian.isAlive()) return;

        // Keep the invisible guardian positioned precisely at the zombie's head level
        guardian.setPosition(mob.getX(), mob.getEyeY() - 0.2, mob.getZ());
        guardian.setVelocity(Vec3d.ZERO);
        guardian.setYaw(mob.getYaw());
        guardian.setPitch(mob.getPitch());

        // Cooldown phase: beam is inactive
        if (active.laserCooldownTicks > 0) {
            active.laserCooldownTicks--;
            if (active.laserChargeTicks > 0) {
                ((GuardianEntityAccessor) guardian).callSetBeamTarget(0);
                active.laserChargeTicks = 0;
                active.laserTarget = null;
            }
            return;
        }

        // Validate or acquire target
        ServerPlayerEntity target = active.laserTarget;
        if (target == null || !target.isAlive() || target.isSpectator() || dev.frost.miniverse.minigame.core.freeze.DownedPlayerTracker.isDowned(target.getUuid()) || target.squaredDistanceTo(mob) > MAX_RANGE_SQ || !hasLineOfSight(world, mob, target)) {
            // Target lost, invalid, downed, or behind solid blocks
            if (active.laserChargeTicks > 0) {
                ((GuardianEntityAccessor) guardian).callSetBeamTarget(0);
                active.laserChargeTicks = 0;
                active.laserTarget = null;
                active.laserCooldownTicks = 20; // 1s grace period before re-targeting
                return;
            }

            // Find closest candidate with direct line-of-sight
            target = null;
            double closestSq = MAX_RANGE_SQ;
            for (ServerPlayerEntity p : survivors) {
                if (p.isAlive() && !p.isSpectator() && !dev.frost.miniverse.minigame.core.freeze.DownedPlayerTracker.isDowned(p.getUuid())) {
                    double distSq = p.squaredDistanceTo(mob);
                    if (distSq < closestSq && hasLineOfSight(world, mob, p)) {
                        closestSq = distSq;
                        target = p;
                    }
                }
            }
            active.laserTarget = target;
        }

        // If no target with line-of-sight is available, keep beam off
        if (target == null) {
            if (active.laserChargeTicks > 0) {
                ((GuardianEntityAccessor) guardian).callSetBeamTarget(0);
                active.laserChargeTicks = 0;
            }
            return;
        }

        // Active target with line of sight: charge the laser
        active.laserChargeTicks++;
        ((GuardianEntityAccessor) guardian).callSetBeamTarget(target.getId());

        // Play charge-up hum periodically
        if (active.laserChargeTicks == 1 || active.laserChargeTicks % 20 == 0) {
            world.playSound(
                null,
                mob.getX(),
                mob.getY(),
                mob.getZ(),
                SoundEvents.ENTITY_GUARDIAN_ATTACK,
                SoundCategory.HOSTILE,
                1.0f,
                0.8f + (active.laserChargeTicks / (float) CHARGE_TICKS) * 0.4f
            );
        }

        // Laser fully charged: strike!
        if (active.laserChargeTicks >= CHARGE_TICKS) {
            target.damage(world.getDamageSources().magic(), damage);
            world.playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.ENTITY_GUARDIAN_ATTACK,
                SoundCategory.HOSTILE,
                1.2f,
                1.5f
            );

            // Turn off beam and enter cooldown
            ((GuardianEntityAccessor) guardian).callSetBeamTarget(0);
            active.laserChargeTicks = 0;
            active.laserTarget = null;
            active.laserCooldownTicks = COOLDOWN_TICKS;
        }
    }

    private static boolean hasLineOfSight(ServerWorld world, MobEntity mob, ServerPlayerEntity target) {
        Vec3d eyePos = mob.getEyePos();
        Vec3d targetEye = target.getEyePos();
        BlockHitResult hit = world.raycast(new RaycastContext(
            eyePos,
            targetEye,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mob
        ));
        return hit.getType() == HitResult.Type.MISS;
    }
}
