package dev.frost.miniverse.minigame.core.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerJoinAware {
    void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server);
}
