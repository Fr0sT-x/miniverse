package dev.frost.miniverse.minigame.impl.deathswap;

public final class DeathSwapGameEvents {
    private DeathSwapGameEvents() {
    }

    public static void register() {
        DeathSwapSessionBootstrap.register();
    }
}


