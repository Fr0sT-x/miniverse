package dev.frost.miniverse.minigame.impl.resourcesprint.death;

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
 * ResourceSprint uses vanilla death (DO_IMMEDIATE_RESPAWN = false).
 * F05 is connected so callbacks can be used in the future.
 */
public class ResourceSprintDeathLifecycleConfig implements DeathLifecycleConfig {
    private final ResourceSprintDeathCallbacks callbacks = new ResourceSprintDeathCallbacks();

    @Override
    public DeathPolicy getDeathPolicy() {
        return new VanillaDeathPolicy();
    }

    @Override
    public DeathSpectatorPolicy getSpectatorPolicy() {
        return new FreeFlySpectatorPolicy(SpectatorService.getInstance());
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy() {
        return new SpectateForeverPolicy();
    }

    @Override
    public RespawnStrategy getRespawnStrategy() {
        return (context, spectatorSession) -> null;
    }

    @Override
    public DeathLifecycleCallbacks getCallbacks() {
        return this.callbacks;
    }

    @Override
    @Nullable
    public String resolveTeamId(UUID playerId) {
        return null;
    }

    @Override
    @Nullable
    public String resolveMatchIdentifier() {
        return null;
    }
}
