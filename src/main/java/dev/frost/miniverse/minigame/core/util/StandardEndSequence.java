package dev.frost.miniverse.minigame.core.util;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;

public class StandardEndSequence {

    private StandardEndSequence() {}

    public static void start(String minigameName, MatchEndResult result) {
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            MatchLifecycleController.getInstance().endMatch(runtime, result, MatchLifecycleOptions.defaults(minigameName));
        }
    }
}
