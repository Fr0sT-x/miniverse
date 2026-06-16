package dev.frost.miniverse.minigame.core.death;

public enum CancellationReason {
    DISCONNECT,
    MATCH_ENDING,
    FORCED_REMOVAL,
    SERVER_SHUTDOWN,
    ELIMINATION,
    ADMIN_ACTION,
    UNKNOWN
}
