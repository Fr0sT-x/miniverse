package dev.frost.miniverse.minigame.core;

/**
 * Represents the various states of a minigame lifecycle.
 * This enum defines the transitions a minigame can go through.
 */
public enum GameState {
    WAITING,

    COUNTDOWN,

    /**
     * The game is waiting for enough players to join before starting.
     */
    WAITING_FOR_PLAYERS,

    /**
     * The game is in the startup phase, initializing and preparing to begin.
     */
    STARTING,

    FROZEN,

    /**
     * The game is actively running.
     */
    RUNNING,

    PLAYING,

    IN_PROGRESS,

    /**
     * The game is temporarily paused by an administrator.
     */
    PAUSED,

    /**
     * The game is ending or has ended.
     */
    ENDING,

    RETURNING,

    ROUND_RESET,

    MATCH_OVER,

    FINISHED;

    /**
     * Checks if the game is currently active (in progress).
     */
    public boolean isActive() {
        return this == RUNNING || this == IN_PROGRESS || this == PLAYING;
    }

    /**
     * Checks if the game is in a terminal state.
     */
    public boolean isTerminal() {
        return this == ENDING || this == RETURNING || this == FINISHED || this == MATCH_OVER;
    }
}

