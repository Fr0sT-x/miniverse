package dev.frost.miniverse.minigame.impl.horde;

public final class HordeSurvivalGameEvents {
    private HordeSurvivalGameEvents() {
    }

    public static void register() {
        HordeSurvivalSessionBootstrap.register();
    }
}
