package dev.frost.miniverse.minigame.impl.murdermystery.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.impl.murdermystery.MurderMysteryMinigame;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

public class MurderMysteryRespawnStrategy implements RespawnStrategy {
    private final MurderMysteryMinigame minigame;

    public MurderMysteryRespawnStrategy(MurderMysteryMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession currentSession) {
        // Stay at the death location as a spectator — there's no "respawn" in Murder Mystery,
        // only permanent spectating. The player should observe the game from where they fell,
        // not be teleported to an arbitrary spawn point.
        ServerPlayerEntity victim = minigame.getPlayerByUuid(context.victimId());
        if (victim != null) {
            return new RespawnLocation(victim.getServerWorld(), context.location(), context.yawAtDeath(), context.pitchAtDeath());
        }
        ServerWorld fallbackWorld = minigame.getContext() != null && minigame.getContext().nullableServer() != null
            ? minigame.getContext().nullableServer().getWorld(context.dimension())
            : null;
        return new RespawnLocation(fallbackWorld, context.location(), context.yawAtDeath(), context.pitchAtDeath());
    }
}
