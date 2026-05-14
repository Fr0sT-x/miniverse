package dev.frost.miniverse.client.freeze;

public final class ClientFreezeState {
    private static boolean frozen;

    private ClientFreezeState() {
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static void setFrozen(boolean value) {
        frozen = value;
    }
}

