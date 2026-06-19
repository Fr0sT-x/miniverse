package dev.frost.miniverse.minigame.core.event;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Implemented by minigames that have named spawn points players should be
 * teleported to when they first join a session.
 *
 * <p>The framework calls {@link #teleportToSpawn(ServerPlayerEntity)} from
 * {@code SessionBootstrapper.State.onJoin()} <em>after</em> {@code applySettings()}
 * has already run, guaranteeing that any map config (spawn positions etc.) is
 * fully initialised before the teleport is attempted.
 *
 * <p>This replaces per-gamemode boilerplate in {@code Handler.onPlayerJoin}.
 */
public interface SpawnPointAware {

    /**
     * Teleport {@code player} to an appropriate spawn location for the current
     * game state (e.g. a random spawn marker during the freeze/transition phase).
     *
     * <p>Implementations should be a no-op when no spawn points are configured
     * or when the current game state makes a teleport inappropriate.
     */
    void teleportToSpawn(ServerPlayerEntity player);
}
