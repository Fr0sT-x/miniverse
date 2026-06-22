package dev.frost.miniverse.minigame.impl.deathswap.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapMinigame;
import net.minecraft.server.network.ServerPlayerEntity;

public class DeathSwapDeathPolicy implements DeathPolicy {
    private final DeathSwapMinigame minigame;

    public DeathSwapDeathPolicy(DeathSwapMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public boolean interceptsRespawn() {
        return true;
    }

    @Override
    public void execute(ServerPlayerEntity victim, DeathContext context) {
        this.minigame.processPlayerDeath(victim);
    }
}
