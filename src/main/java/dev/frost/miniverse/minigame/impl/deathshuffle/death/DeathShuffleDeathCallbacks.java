package dev.frost.miniverse.minigame.impl.deathshuffle.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.impl.deathshuffle.DeathShuffleMinigame;
import net.minecraft.server.network.ServerPlayerEntity;

public class DeathShuffleDeathCallbacks implements DeathLifecycleCallbacks {
    private final DeathShuffleMinigame minigame;

    public DeathShuffleDeathCallbacks(DeathShuffleMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public void onDeath(ServerPlayerEntity victim, DeathContext context) {
        this.minigame.processPlayerDeath(victim, context);
    }

    @Override
    public void onRespawnComplete(ServerPlayerEntity player, DeathContext context) {
    }
}
