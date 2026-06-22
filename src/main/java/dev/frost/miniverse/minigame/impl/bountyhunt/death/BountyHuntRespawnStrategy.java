package dev.frost.miniverse.minigame.impl.bountyhunt.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntMinigame;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class BountyHuntRespawnStrategy implements RespawnStrategy {
    private final BountyHuntMinigame minigame;

    public BountyHuntRespawnStrategy(BountyHuntMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession currentSession) {
        ServerWorld world = minigame.getContext() != null && minigame.getContext().nullableServer() != null
            ? minigame.getContext().nullableServer().getWorld(context.dimension())
            : null;

        if (world == null) {
            return null;
        }

        // BountyHunt does not use configured Map spawn points.
        // It relies on the vanilla world spawn logic for respawning, which includes beds.
        return RespawnStrategy.resolveVanillaSpawn(world.getServer(), context.victimId(), world);
    }
}
