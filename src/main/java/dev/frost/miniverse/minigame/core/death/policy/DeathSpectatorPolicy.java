package dev.frost.miniverse.minigame.core.death.policy;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import net.minecraft.server.network.ServerPlayerEntity;

public interface DeathSpectatorPolicy {
    /**
     * Applies the spectator behavior to the dead player.
     *
     * @param player  the player who died
     * @param context the context of the death
     */
    void apply(ServerPlayerEntity player, DeathContext context);

    /**
     * @return true if the spectator should have a fixed camera, false if free-fly is allowed
     */
    boolean requiresFixedCamera();

    /**
     * @return the policy to apply if no valid target exists
     */
    NoTargetPolicy noTargetPolicy();
}
