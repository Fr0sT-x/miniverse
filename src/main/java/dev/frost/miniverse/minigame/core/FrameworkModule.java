package dev.frost.miniverse.minigame.core;

import net.minecraft.server.MinecraftServer;

/**
 * Represents an optional framework module that can be opted into by minigames.
 * Modules implementing this interface can be auto-registered for cleanup when the game stops.
 */
public interface FrameworkModule {
    /**
     * Cleans up any resources or state held by this module.
     * @param server The Minecraft server
     */
    void cleanup(MinecraftServer server);
}
