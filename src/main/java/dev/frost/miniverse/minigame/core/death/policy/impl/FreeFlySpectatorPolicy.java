package dev.frost.miniverse.minigame.core.death.policy.impl;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import net.minecraft.server.network.ServerPlayerEntity;

public class FreeFlySpectatorPolicy implements DeathSpectatorPolicy {
    private final SpectatorService spectatorService;

    public FreeFlySpectatorPolicy(SpectatorService spectatorService) {
        this.spectatorService = spectatorService;
    }

    @Override
    public void apply(ServerPlayerEntity victim, DeathContext context) {
        this.spectatorService.startSpectating(
            victim,
            SpectatorPolicies.unrestricted(),
            SpectatorTargetProviders.none(),
            SpectatorMode.STANDARD,
            null,
            null,
            null
        );
    }

    @Override
    public boolean requiresFixedCamera() {
        return false;
    }

    @Override
    public NoTargetPolicy noTargetPolicy() {
        return NoTargetPolicy.FREE_FLY;
    }
}
