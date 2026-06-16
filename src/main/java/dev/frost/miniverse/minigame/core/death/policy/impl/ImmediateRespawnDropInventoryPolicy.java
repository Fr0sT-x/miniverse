package dev.frost.miniverse.minigame.core.death.policy.impl;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import net.minecraft.server.network.ServerPlayerEntity;

public class ImmediateRespawnDropInventoryPolicy implements DeathPolicy {
    @Override
    public boolean interceptsRespawn() {
        return true;
    }

    @Override
    public void execute(ServerPlayerEntity victim, DeathContext context) {
        victim.getInventory().dropAll();
        victim.getInventory().clear();
        victim.extinguish();
        victim.clearStatusEffects();
        victim.setHealth(20.0f);
    }
}
