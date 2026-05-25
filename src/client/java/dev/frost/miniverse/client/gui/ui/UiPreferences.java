package dev.frost.miniverse.client.gui.ui;

public final class UiPreferences {
    private static boolean worldBackdropEnabled = true;

    private UiPreferences() {
    }

    public static boolean worldBackdropEnabled() {
        return worldBackdropEnabled;
    }

    public static void setWorldBackdropEnabled(boolean enabled) {
        worldBackdropEnabled = enabled;
    }
}
