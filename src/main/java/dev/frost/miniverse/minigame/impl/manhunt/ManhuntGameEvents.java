package dev.frost.miniverse.minigame.impl.manhunt;

/**
 * Server-side Fabric events for the Manhunt module.
 * Keeps input handling decoupled from the core game logic.
 */
public final class ManhuntGameEvents {
    private ManhuntGameEvents() {
    }

    public static void register() {
        ManhuntSessionBootstrap.register();
    }
}
