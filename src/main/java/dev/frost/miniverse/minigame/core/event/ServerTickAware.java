package dev.frost.miniverse.minigame.core.event;

import net.minecraft.server.MinecraftServer;

public interface ServerTickAware {
    void onServerTick(MinecraftServer server);
}
