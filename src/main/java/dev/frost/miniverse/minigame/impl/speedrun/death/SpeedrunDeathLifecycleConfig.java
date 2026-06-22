package dev.frost.miniverse.minigame.impl.speedrun.death;

import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.death.policy.impl.FreeFlySpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.SpectateForeverPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.VanillaDeathPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Speedrun is intentionally NOT an elimination gamemode: death is a non-event,
 * players respawn via vanilla/PlayerRespawnAware and keep running.
 *
 * F05 is wired here ONLY so handleFatalDamage()'s death callbacks (onDeath /
 * onDeathProcessed) have a framework home for future use (death counter, sound, etc).
 *
 * Because VanillaDeathPolicy.interceptsRespawn() == false, DeathLifecycleManager
 * never invokes getSpectatorPolicy(), getRespawnStrategy(), createPostDeathPolicy(),
 * or resolveRespawnGameMode() (see DeathLifecycleManager.handleFatalDamage, line ~91
 * branch). The values below exist only to satisfy the non-null interface contract.
 *
 * TODO: unused while DeathPolicy.interceptsRespawn() == false — do not build logic
 * on top of these until/unless Speedrun actually adopts elimination semantics.
 */
public class SpeedrunDeathLifecycleConfig implements DeathLifecycleConfig {
    private final SpeedrunDeathCallbacks callbacks = new SpeedrunDeathCallbacks();

    @Override
    public DeathPolicy getDeathPolicy() {
        return new VanillaDeathPolicy();
    }

    @Override
    public DeathSpectatorPolicy getSpectatorPolicy() {
        // TODO: unused while DeathPolicy.interceptsRespawn() == false
        return new FreeFlySpectatorPolicy(SpectatorService.getInstance());
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy() {
        // TODO: unused while DeathPolicy.interceptsRespawn() == false
        return new SpectateForeverPolicy();
    }

    @Override
    public RespawnStrategy getRespawnStrategy() {
        // TODO: unused while DeathPolicy.interceptsRespawn() == false
        return (context, spectatorSession) -> null; // Anonymous implementation instead of DeathLocationStrategy which requires a non-null MinecraftServer
    }

    @Override
    public DeathLifecycleCallbacks getCallbacks() {
        return this.callbacks;
    }

    @Override
    @Nullable
    public String resolveTeamId(UUID playerId) {
        return null; // Speedrun is not team-based (isTeamBased() == false)
    }

    @Override
    @Nullable
    public String resolveMatchIdentifier() {
        return null;
    }
}
