package dev.frost.miniverse.minigame.core.event;

import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerRespawnAware {
    void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive);
}
