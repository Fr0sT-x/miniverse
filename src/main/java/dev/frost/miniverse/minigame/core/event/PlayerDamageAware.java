package dev.frost.miniverse.minigame.core.event;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerDamageAware {
    boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount);
}
