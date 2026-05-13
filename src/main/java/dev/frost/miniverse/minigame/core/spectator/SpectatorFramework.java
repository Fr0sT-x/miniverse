package dev.frost.miniverse.minigame.core.spectator;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectatorFramework {
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();

    public void enter(ServerPlayerEntity player, Text reason) {
        this.spectators.add(player.getUuid());
        player.changeGameMode(GameMode.SPECTATOR);
        player.sendMessage(reason.copy().formatted(Formatting.GRAY), false);
    }

    public void restore(ServerPlayerEntity player) {
        this.spectators.remove(player.getUuid());
        player.changeGameMode(GameMode.SURVIVAL);
    }

    public boolean isSpectating(UUID playerId) {
        return this.spectators.contains(playerId);
    }

    public void clear() {
        this.spectators.clear();
    }
}
