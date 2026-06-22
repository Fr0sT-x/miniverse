package dev.frost.miniverse.minigame.impl.infection.death;

import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.impl.infection.InfectionMinigame;
import net.minecraft.server.network.ServerPlayerEntity;

public class InfectionDeathPolicy implements DeathPolicy {
    private final InfectionMinigame minigame;

    public InfectionDeathPolicy(InfectionMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public boolean interceptsRespawn() {
        return true;
    }

    @Override
    public void execute(ServerPlayerEntity victim, DeathContext context) {
        if (this.minigame.isSurvivor(victim)) {
            this.minigame.processSurvivorDeath(victim);
        }
    }
}
