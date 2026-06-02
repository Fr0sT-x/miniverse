package dev.frost.miniverse.minigame.impl.infection;

public final class InfectionGameEvents {
    private static boolean registered;

    private InfectionGameEvents() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        InfectionSessionBootstrap.register();
        registered = true;
    }
}
