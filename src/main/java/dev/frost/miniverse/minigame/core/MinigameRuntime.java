package dev.frost.miniverse.minigame.core;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public final class MinigameRuntime {
    private final Minigame minigame;
    private final MinigameContext context;
    private GameState state;

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

    public void stop() {
        this.minigame.stopGame();
    }
}
