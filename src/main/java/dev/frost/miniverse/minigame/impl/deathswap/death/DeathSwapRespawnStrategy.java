package dev.frost.miniverse.minigame.impl.deathswap.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapMinigame;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class DeathSwapRespawnStrategy implements RespawnStrategy {
    private final DeathSwapMinigame minigame;

    public DeathSwapRespawnStrategy(DeathSwapMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession currentSession) {
        ServerWorld world = minigame.context() != null && minigame.context().nullableServer() != null
            ? minigame.context().nullableServer().getWorld(context.dimension())
            : null;
        if (world == null) {
            return null;
        }

        return RespawnStrategy.resolveVanillaSpawn(world.getServer(), context.victimId(), world);
    }
}
