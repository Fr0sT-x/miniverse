package dev.frost.miniverse.minigame.core;

/**
 * Optional hook for minigames that need to react when an administrator pauses
 * or resumes a live match.
 */
public interface PauseAwareMinigame {
    default void onPause(GameState previousState) {
    }

    default void onResume(GameState resumedState) {
    }
}
