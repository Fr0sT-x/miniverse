package dev.frost.miniverse.minigame.core.event;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;

public interface EntityDamageAware {
    boolean allowEntityDamage(LivingEntity entity, DamageSource source, float amount);
}
