package dev.frost.miniverse.team;

public enum TeamRole {
    MEMBER("Member"),
    RUNNER("Runner"),
    HUNTER("Hunter"),
    SPECTATOR("Spectator"),
    DEAD("Dead"),
    TARGET("Target");

    private final String displayName;

    TeamRole(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}
