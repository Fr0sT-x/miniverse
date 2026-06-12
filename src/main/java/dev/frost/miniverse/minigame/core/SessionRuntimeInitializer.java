package dev.frost.miniverse.minigame.core;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.SessionBootstrapper.Handler;

import java.util.Properties;

public class SessionRuntimeInitializer<T extends Minigame> {
    private final Handler<T> handler;
    private boolean settingsApplied;
    private boolean savedStateLoaded;

    public SessionRuntimeInitializer(Handler<T> handler) {
        this.handler = handler;
    }

    public T getOrCreateRuntime() {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (this.handler.runtimeType().isInstance(active)) {
            return this.handler.runtimeType().cast(active);
        }

        if (active != null) {
            Miniverse.LOGGER.warn(
                "Session config requested {} but active minigame is {}.",
                this.handler.gameId(),
                active.getName()
            );
            return null;
        }

        T minigame = this.handler.createRuntime();
        MinigameManager.getInstance().setActiveMinigame(minigame);
        return minigame;
    }

    public void applySettingsIfNecessary(T minigame, Properties properties) {
        if (!this.settingsApplied) {
            this.handler.applySettings(minigame, properties);
            if (minigame instanceof AbstractMinigame am) {
                am.applyGameRulesOverrides(properties);
            }
            this.settingsApplied = true;
        }
    }

    public void loadSavedStateIfPresent(Properties properties) {
        if (this.savedStateLoaded) {
            return;
        }
        this.savedStateLoaded = true;
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            SessionRestoreCoordinator.restoreRuntimeIfPresent(
                runtime,
                this.handler.lifecycleOptions(this.handler.runtimeType().cast(runtime.minigame()), properties),
                runtime.minigame()::startGame
            );
        }
    }
}
