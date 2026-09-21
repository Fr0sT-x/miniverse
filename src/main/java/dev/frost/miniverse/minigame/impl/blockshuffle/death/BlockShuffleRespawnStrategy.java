package dev.frost.miniverse.minigame.impl.blockshuffle.death;

import org.jetbrains.annotations.Nullable;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.impl.blockshuffle.BlockShuffleMinigame;
import net.minecraft.server.world.ServerWorld;

public class BlockShuffleRespawnStrategy implements RespawnStrategy {
    private final BlockShuffleMinigame minigame;

    public BlockShuffleRespawnStrategy(BlockShuffleMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession currentSession) {
        ServerWorld world = this.minigame.getContext() != null && this.minigame.getContext().nullableServer() != null
            ? this.minigame.getContext().nullableServer().getWorld(context.dimension())
            : null;

        if (world == null) {
            return null;
        }

        return RespawnStrategy.resolveVanillaSpawn(world.getServer(), context.victimId(), world);
    }
}
