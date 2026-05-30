package dev.frost.miniverse.minigame.core.session;

import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.persistence.SessionData;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public final class PersistentSession {
    private SessionData data;
    @Nullable
    private MinigameRuntime runtime;
    private Instant lastTouched;

    public PersistentSession(SessionData data, @Nullable MinigameRuntime runtime) {
        this.data = data;
        this.runtime = runtime;
        this.lastTouched = Instant.now();
    }

    public String sessionId() {
        return this.data.sessionId();
    }

    public String gameType() {
        return this.data.gameType();
    }

    public SessionData data() {
        return this.data;
    }

    public void replaceData(SessionData data) {
        this.data = data;
        this.touch();
    }

    @Nullable
    public MinigameRuntime runtime() {
        return this.runtime;
    }

    public void attachRuntime(@Nullable MinigameRuntime runtime) {
        this.runtime = runtime;
        this.touch();
    }

    public Instant lastTouched() {
        return this.lastTouched;
    }

    public void touch() {
        this.lastTouched = Instant.now();
    }
}
