package dev.frost.miniverse.minigame.impl.deathswap.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapMinigame;
import net.minecraft.server.network.ServerPlayerEntity;

public class DeathSwapDeathCallbacks implements DeathLifecycleCallbacks {
    private final DeathSwapMinigame minigame;

    public DeathSwapDeathCallbacks(DeathSwapMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public void onRespawnComplete(ServerPlayerEntity player, DeathContext context) {
        this.minigame.processPlayerRespawn(player);
    }
}
