package dev.frost.miniverse.minigame.impl.manhunt.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.FreeFlySpectatorPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame.ManhuntRole;
import net.minecraft.server.network.ServerPlayerEntity;

public class ManhuntSpectatorPolicy implements DeathSpectatorPolicy {
    private final ManhuntMinigame minigame;
    public ManhuntSpectatorPolicy(ManhuntMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public void apply(ServerPlayerEntity player, DeathContext context) {
        SpectatorService.getInstance().startSpectating(
            player,
            SpectatorPolicies.teamOnly(this.minigame.teamManager(), true),
            SpectatorTargetProviders.roster(),
            SpectatorMode.STANDARD,
            null, null, null, NoTargetPolicy.STATIONARY_FREE_FLY
        );
    }

    @Override
    public boolean requiresFixedCamera() {
        return false;
    }

    @Override
    public NoTargetPolicy noTargetPolicy() {
        return NoTargetPolicy.STATIONARY_FREE_FLY;
    }
}
