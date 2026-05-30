package dev.frost.miniverse.minigame.impl.manhunt;

import dev.frost.miniverse.minigame.core.session.MinigameSessionManager;

/**
 * Server-side Fabric events for the Manhunt module.
 * Keeps input handling decoupled from the core game logic.
 */
public final class ManhuntGameEvents {
    private ManhuntGameEvents() {
    }

    public static void register() {
        MinigameSessionManager.getInstance().registerSerializer(new ManhuntSessionSerializer());
        ManhuntSessionBootstrap.register();
    }
}
