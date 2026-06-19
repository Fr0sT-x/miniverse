package dev.frost.miniverse.minigame.impl.murdermystery.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import net.minecraft.server.network.ServerPlayerEntity;

public class MurderMysteryDeathPolicy implements DeathPolicy {
    @Override
    public void execute(ServerPlayerEntity victim, DeathContext context) {
        victim.setHealth(20.0f);
        victim.clearStatusEffects();
        victim.getInventory().clear();
    }

    @Override
    public boolean interceptsRespawn() {
        return true;
    }
}
