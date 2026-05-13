package dev.frost.miniverse.minigame.core.respawn;

import dev.frost.miniverse.minigame.core.spectator.SpectatorFramework;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

public final class RespawnPolicyController {
    private final RespawnMode mode;
    private final SpectatorFramework spectators;

    public RespawnPolicyController(RespawnMode mode, SpectatorFramework spectators) {
        this.mode = mode == null ? RespawnMode.POINTS : mode;
        this.spectators = spectators;
    }

    public RespawnMode mode() {
        return this.mode;
    }

    public void handleDeath(ServerPlayerEntity player, Text eliminationReason) {
        if (this.mode == RespawnMode.ELIMINATION) {
            this.spectators.enter(player, eliminationReason);
        }
    }

    public void handleRespawn(ServerPlayerEntity player) {
        if (this.mode == RespawnMode.ELIMINATION && this.spectators.isSpectating(player.getUuid())) {
            player.changeGameMode(GameMode.SPECTATOR);
            return;
        }
        player.changeGameMode(GameMode.SURVIVAL);
    }
}
