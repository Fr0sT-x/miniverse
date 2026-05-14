package dev.frost.miniverse.minigame.core.respawn;

import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

public final class RespawnPolicyController {
    private final RespawnMode mode;
    private final SpectatorService spectators;

    public RespawnPolicyController(RespawnMode mode, SpectatorService spectators) {
        this.mode = mode == null ? RespawnMode.POINTS : mode;
        this.spectators = spectators == null ? SpectatorService.getInstance() : spectators;
    }

    public RespawnMode mode() {
        return this.mode;
    }

    public void handleDeath(ServerPlayerEntity player, Text eliminationReason) {
        if (this.mode == RespawnMode.ELIMINATION) {
            this.spectators.startSpectating(
                player,
                SpectatorPolicies.unrestricted(),
                SpectatorTargetProviders.none(),
                SpectatorMode.ELIMINATED,
                null,
                null,
                eliminationReason
            );
        }
    }

    public void handleRespawn(ServerPlayerEntity player) {
        if (this.mode == RespawnMode.ELIMINATION && this.spectators.isSpectating(player.getUuid())) {
            if (this.spectators.ensureSpectating(player)) {
                return;
            }
            player.changeGameMode(GameMode.SPECTATOR);
            return;
        }
        player.changeGameMode(GameMode.SURVIVAL);
    }
}
