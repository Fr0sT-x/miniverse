package dev.frost.miniverse.minigame.core.spectator;

import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * @deprecated Use {@link SpectatorService} with policies/providers instead.
 */
@Deprecated
public final class SpectatorFramework {
    private final SpectatorService service = SpectatorService.getInstance();

    public void enter(ServerPlayerEntity player, Text reason) {
        if (player == null) {
            return;
        }
        Text message = reason == null ? null : reason.copy().formatted(Formatting.GRAY);
        this.service.startSpectating(
            player,
            SpectatorPolicies.unrestricted(),
            SpectatorTargetProviders.none(),
            SpectatorMode.STANDARD,
            null,
            null,
            message
        );
    }

    public void restore(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        this.service.stopSpectating(player, SpectatorStopReason.MANUAL);
    }

    public boolean isSpectating(UUID playerId) {
        return this.service.isSpectating(playerId);
    }

    public void clear() {
        this.service.clearAll();
    }
}
