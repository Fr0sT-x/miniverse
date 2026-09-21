package dev.frost.miniverse.minigame.impl.manhunt.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
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
        // Target provider is filtered to alive speedrunners ONLY.
        // This ensures the spectator hotbar (pressing 1-9) can only TP to fellow speedrunners,
        // never to hunters — even in the brief window before the next validation tick.
        // allowTargetSwitching=true is intentional: dead runners may switch between alive speedrunners freely.
        SpectatorService.getInstance().startSpectating(
            player,
            SpectatorPolicies.teamOnly(this.minigame.teamManager(), true),
            SpectatorTargetProviders.filtered(
                SpectatorTargetProviders.roster(),
                target -> {
                    if (!(target instanceof ServerPlayerEntity targetPlayer)) return false;
                    ManhuntRole role = this.minigame.getPlayerRole(targetPlayer);
                    return role == ManhuntRole.SPEEDRUNNER && !targetPlayer.isSpectator();
                }
            ),
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
