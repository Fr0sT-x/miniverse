package dev.frost.miniverse.minigame.impl.blockshuffle.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.impl.blockshuffle.BlockShuffleMinigame;
import net.minecraft.server.network.ServerPlayerEntity;

public class BlockShuffleDeathCallbacks implements DeathLifecycleCallbacks {
    private final BlockShuffleMinigame minigame;

    public BlockShuffleDeathCallbacks(BlockShuffleMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public void onDeath(ServerPlayerEntity victim, DeathContext context) {
    }

    @Override
    public void onRespawnComplete(ServerPlayerEntity player, DeathContext context) {
    }
}
