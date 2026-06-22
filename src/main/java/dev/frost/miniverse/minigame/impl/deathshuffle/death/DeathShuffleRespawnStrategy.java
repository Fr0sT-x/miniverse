package dev.frost.miniverse.minigame.impl.deathshuffle.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.impl.deathshuffle.DeathShuffleMinigame;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class DeathShuffleRespawnStrategy implements RespawnStrategy {
    private final DeathShuffleMinigame minigame;

    public DeathShuffleRespawnStrategy(DeathShuffleMinigame minigame) {
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
