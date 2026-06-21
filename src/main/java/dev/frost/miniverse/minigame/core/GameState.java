package dev.frost.miniverse.minigame.core;

/**
 * Represents the various states of a minigame lifecycle.
 * This enum defines the transitions a minigame can go through.
 */
public enum GameState {
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

    /**
     * The game is temporarily paused by an administrator.
     */
    PAUSED,

    /**
     * The game is ending or has ended.
     */
    ENDING,

    ROUND_RESET,

    /**
     * The game is completely stopped and session is over.
     */
    STOPPED;

    /**
     * Checks if the game is currently active (in progress).
     */
    public boolean isActive() {
        return this == RUNNING;
    }

    /**
     * Checks if the game is in a terminal state.
     */
    public boolean isTerminal() {
        return this == ENDING || this == STOPPED;
    }
}

