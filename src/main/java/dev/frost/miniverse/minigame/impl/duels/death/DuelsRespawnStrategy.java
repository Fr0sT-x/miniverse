package dev.frost.miniverse.minigame.impl.duels.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.impl.duels.DuelsMinigame;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the dead player at their death position for spectating.
 * The actual "respawn" (teleport to spawn + fresh kit) is handled by
 * DuelMatch.startRound() when the next round begins.
 */
public class DuelsRespawnStrategy implements RespawnStrategy {
    private final DuelsMinigame minigame;

    public DuelsRespawnStrategy(DuelsMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession currentSession) {
        // Stay at the exact death location — there is no "respawn" within the framework flow
        // for Duels. When the round ends, DuelMatch.startRound() handles the re-entry.
        ServerPlayerEntity victim = minigame.getPlayerByUuid(context.victimId());
        if (victim != null) {
            return new RespawnLocation(
                victim.getServerWorld(),
                context.location(),
                context.yawAtDeath(),
                context.pitchAtDeath()
            );
        }
        // Fallback: use the world from context dimension
        ServerWorld fallbackWorld = minigame.getContext() != null && minigame.getContext().nullableServer() != null
            ? minigame.getContext().nullableServer().getWorld(context.dimension())
            : null;
        return new RespawnLocation(fallbackWorld, context.location(), context.yawAtDeath(), context.pitchAtDeath());
    }
}
