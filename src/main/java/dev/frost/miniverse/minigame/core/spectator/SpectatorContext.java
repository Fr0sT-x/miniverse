package dev.frost.miniverse.minigame.core.spectator;

import dev.frost.miniverse.minigame.core.MinigameContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record SpectatorContext(
    @Nullable MinigameContext minigameContext,
    MinecraftServer server,
    UUID spectatorId,
    @Nullable ServerPlayerEntity spectator
) {
    public List<ServerPlayerEntity> liveParticipants() {
        if (this.minigameContext == null) {
            return this.server.getPlayerManager().getPlayerList();
        }
        return this.minigameContext.liveParticipants();
    }
}

