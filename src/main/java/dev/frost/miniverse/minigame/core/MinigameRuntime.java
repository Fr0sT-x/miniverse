package dev.frost.miniverse.minigame.core;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public final class MinigameRuntime {
    private final Minigame minigame;
    private final MinigameContext context;
    private GameState state;
    private GameState stateBeforePause;

    public MinigameRuntime(Minigame minigame, @Nullable MinecraftServer server) {
        this.minigame = minigame;
        this.context = new MinigameContext();
        this.context.attachStateUpdater(this::setState);
        if (server != null) {
            this.context.bindServer(server);
        }
        if (minigame instanceof RuntimeContextAware contextAware) {
            contextAware.attachContext(this.context);
        }
        this.state = GameState.WAITING_FOR_PLAYERS;
    }

    public Minigame minigame() {
        return this.minigame;
    }

    public MinigameContext context() {
        return this.context;
    }

    public GameState state() {
        return this.state;
    }

    public void bindServer(MinecraftServer server) {
        this.context.bindServer(server);
    }

    public void initialize() {
        this.context.clock().reset();
        this.minigame.initialize();
    }

    public void setState(GameState state) {
        this.state = state;
        this.minigame.setState(state);
    }

    public boolean pause() {
        if (this.state == GameState.PAUSED || this.state == null || this.state.isTerminal()) {
            return false;
        }
        this.stateBeforePause = this.state;
        this.setState(GameState.PAUSED);
        if (this.minigame instanceof PauseAwareMinigame pauseAware) {
            pauseAware.onPause(this.stateBeforePause);
        }
        return true;
    }

    public boolean resume() {
        if (this.state != GameState.PAUSED) {
            return false;
        }
        GameState resumedState = this.stateBeforePause == null ? GameState.RUNNING : this.stateBeforePause;
        this.stateBeforePause = null;
        this.setState(resumedState);
        if (this.minigame instanceof PauseAwareMinigame pauseAware) {
            pauseAware.onResume(resumedState);
        }
        return true;
    }

    public void stop() {
        this.minigame.stopGame();
    }
}
