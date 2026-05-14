package dev.frost.miniverse.minigame.core.spectator;

public record SpectatorRestrictions(
    boolean allowFreecam,
    boolean allowTargetSwitching,
    boolean lockCamera,
    boolean allowCrossDimension,
    boolean allowSpectatorTargets
) {
    public static SpectatorRestrictions freecam() {
        return new SpectatorRestrictions(true, true, false, true, true);
    }

    public static SpectatorRestrictions locked() {
        return new SpectatorRestrictions(false, false, true, false, false);
    }

    public static SpectatorRestrictions lockedSwitching() {
        return new SpectatorRestrictions(false, true, true, false, false);
    }
}

