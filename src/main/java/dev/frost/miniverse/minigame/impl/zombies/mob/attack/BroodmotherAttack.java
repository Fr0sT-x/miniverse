package dev.frost.miniverse.minigame.impl.zombies.mob.attack;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.List;
import java.util.function.Consumer;

public final class BroodmotherAttack {
    private BroodmotherAttack() {}

    public static void tickBroodmother(
        MobEntity boss,
        ServerWorld world,
        List<ServerPlayerEntity> survivors,
        int tickCounter,
        Consumer<MobEntity> minionRegistrar
    ) {
        // Slowness Web attack every 120 ticks (6s)
        if (tickCounter % 120 == 0) {
            ServerPlayerEntity target = null;
            double closestSq = 16.0 * 16.0;

            for (ServerPlayerEntity p : survivors) {
                if (!p.isSpectator() && p.isAlive()) {
                    double distSq = p.squaredDistanceTo(boss);
                    if (distSq < closestSq) {
                        closestSq = distSq;
                        target = p;
                    }
                }
            }

            if (target != null) {
                world.spawnParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.5, 0.5, 0.5, 0.05);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1, false, true, true));
                world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_SPIDER_HURT, SoundCategory.HOSTILE, 1.2f, 0.5f);
            }
        }

        // Minion Cave Spiders summon every 200 ticks (10s)
        if (tickCounter % 200 == 0) {
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.spawnParticles(ParticleTypes.POOF, boss.getX(), boss.getY() + 0.5, boss.getZ(), 15, 0.8, 0.5, 0.8, 0.1);

            for (int i = 0; i < 2; i++) {
                CaveSpiderEntity minion = new CaveSpiderEntity(EntityType.CAVE_SPIDER, world);
                double offsetX = (Math.random() - 0.5) * 3.0;
                double offsetZ = (Math.random() - 0.5) * 3.0;
                minion.setPosition(boss.getX() + offsetX, boss.getY(), boss.getZ() + offsetZ);
                world.spawnEntity(minion);
                if (minionRegistrar != null) {
                    minionRegistrar.accept(minion);
                }
            }
        }
    }
}
