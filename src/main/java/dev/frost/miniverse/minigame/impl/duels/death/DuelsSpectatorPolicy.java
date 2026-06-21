package dev.frost.miniverse.minigame.impl.duels.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * After a player dies in a Duels round they spectate the remaining alive players
 * in their arena until the round ends, at which point startRound() teleports
 * everyone back with a fresh kit.
 */
public class DuelsSpectatorPolicy implements DeathSpectatorPolicy {
    private final SpectatorService spectatorService;

    public DuelsSpectatorPolicy(SpectatorService spectatorService) {
        this.spectatorService = spectatorService;
    }

    @Override
    public void apply(ServerPlayerEntity victim, DeathContext context) {
        this.spectatorService.startSpectating(
            victim,
            SpectatorPolicies.unrestricted(),
            SpectatorTargetProviders.roster(),
            SpectatorMode.ELIMINATED,
            null,
            null,
            Text.literal("You were eliminated. Spectating until the round ends.").formatted(Formatting.GRAY),
            this.noTargetPolicy()
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
