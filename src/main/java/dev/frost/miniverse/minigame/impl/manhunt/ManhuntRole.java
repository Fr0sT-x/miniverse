package dev.frost.miniverse.minigame.impl.manhunt;

/**
 * Enum representing the different roles a player can have in Manhunt.
 */
public enum ManhuntRole {
    /**
     * A Speedrunner - the hunted player. If they die, the hunters win.
     * There is typically one speedrunner, but multiple can exist.
     */
    SPEEDRUNNER("Speedrunner"),

    /**
     * A Hunter - tries to kill the speedrunner. Can respawn upon death.
     * Multiple hunters can exist.
     */
    HUNTER("Hunter");

    private final String displayName;

    ManhuntRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}

