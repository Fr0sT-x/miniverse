package dev.frost.miniverse.minigame.impl.bountyhunt;

public final class BountyHuntGameEvents {
    private BountyHuntGameEvents() {
    }

    public static void register() {
        BountyHuntSessionBootstrap.register();
    }
}


