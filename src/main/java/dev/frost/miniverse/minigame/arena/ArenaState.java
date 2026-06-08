package dev.frost.miniverse.minigame.arena;

public enum ArenaState {
    /**
     * Arena is empty, clean, and ready to be claimed by a new match.
     */
    IDLE,

    /**
     * Arena has been assigned to a match. Players might be teleporting to it.
     */
    RESERVED,

    /**
     * The match is currently active. Block changes are being tracked.
     */
    RUNNING,

    /**
     * The match has ended and the arena is reverting blocks and cleaning entities.
     */
    RESETTING
}
