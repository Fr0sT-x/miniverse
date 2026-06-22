package dev.frost.miniverse.minigame.impl.infection.death;

import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.impl.infection.InfectionMinigame;
import net.minecraft.server.network.ServerPlayerEntity;

public class InfectionDeathCallbacks implements DeathLifecycleCallbacks {
    private final InfectionMinigame minigame;

    public InfectionDeathCallbacks(InfectionMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public void onDeath(ServerPlayerEntity victim, DeathContext context) {
    }

    @Override
    public void onRespawnComplete(ServerPlayerEntity player, DeathContext context) {
        this.minigame.syncVanillaTeamsPublic();
    }
}
