package dev.frost.miniverse.minigame.core.event;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;

public interface EntityDeathAware {
    void onEntityDeath(LivingEntity entity, DamageSource source);
}
