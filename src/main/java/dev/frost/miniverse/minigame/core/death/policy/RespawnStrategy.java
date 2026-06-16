package dev.frost.miniverse.minigame.core.death.policy;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public interface RespawnStrategy {
    /**
     * Resolves the location where the player should respawn.
     * This method must always return a valid location, utilizing fallbacks if necessary.
     *
     * @param context          the context of the death
     * @param spectatorSession the player's current spectator session, if any
     * @return the resolved respawn location (never null)
     */
    RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession spectatorSession);

    record RespawnLocation(ServerWorld world, Vec3d pos, float yaw, float pitch) {
    }
}
