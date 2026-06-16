package dev.frost.miniverse.minigame.core.death.config;

import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface DeathLifecycleConfig {
    /**
     * @return the policy dictating immediate death processing behavior
     */
    DeathPolicy getDeathPolicy();

    /**
     * @return the policy dictating spectator behavior after death
     */
    DeathSpectatorPolicy getSpectatorPolicy();

    /**
     * Must return a new instance per call.
     * Each active death flow owns its own PostDeathPolicy instance.
     * @return a new policy dictating post-death flow (e.g. respawn timers)
     */
    PostDeathPolicy createPostDeathPolicy();

    /**
     * @return the strategy used to resolve the respawn location
     */
    RespawnStrategy getRespawnStrategy();

    /**
     * @return the gamemode the player should be set to upon respawning
     */
    default net.minecraft.world.GameMode resolveRespawnGameMode() {
        return net.minecraft.world.GameMode.SURVIVAL;
    }

    /**
     * @return optional callbacks to listen for state transitions
     */
    @Nullable
    default DeathLifecycleCallbacks getCallbacks() {
        return null;
    }

    /**
     * @param playerId the player's UUID
     * @return the team id of the player, or null if unassigned
     */
    @Nullable
    String resolveTeamId(UUID playerId);

    /**
     * @return the match identifier, or null if unavailable
     */
    @Nullable
    String resolveMatchIdentifier();
}
