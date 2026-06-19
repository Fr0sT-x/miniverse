package dev.frost.miniverse.minigame.core.death.policy.impl;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.SpectatorPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProvider;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;

public class FixedCameraSpectatorPolicy implements DeathSpectatorPolicy {
    private final SpectatorService spectatorService;
    private final SpectatorPolicy policy;
    private final SpectatorTargetProvider targetProvider;
    private final NoTargetPolicy noTargetPolicy;

    public FixedCameraSpectatorPolicy(SpectatorService spectatorService, SpectatorPolicy policy, SpectatorTargetProvider targetProvider, NoTargetPolicy noTargetPolicy) {
        this.spectatorService = Objects.requireNonNull(spectatorService, "spectatorService cannot be null");
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
        this.targetProvider = Objects.requireNonNull(targetProvider, "targetProvider cannot be null");
        this.noTargetPolicy = Objects.requireNonNull(noTargetPolicy, "noTargetPolicy cannot be null");
    }

    public static FixedCameraSpectatorPolicy withDefaults(SpectatorService spectatorService) {
        return new FixedCameraSpectatorPolicy(
            spectatorService,
            SpectatorPolicies.lockedSwitching(),
            SpectatorTargetProviders.roster(),
            NoTargetPolicy.FREEZE
        );
    }

    @Override
    public void apply(ServerPlayerEntity victim, DeathContext context) {
        this.spectatorService.startSpectating(
            victim,
            this.policy,
            this.targetProvider,
            SpectatorMode.STANDARD,
            context.spectatorTargetAtDeath(),
            null,
            null,
            this.noTargetPolicy
        );
    }

    @Override
    public boolean requiresFixedCamera() {
        return true;
    }

    @Override
    public NoTargetPolicy noTargetPolicy() {
        return this.noTargetPolicy;
    }
}
