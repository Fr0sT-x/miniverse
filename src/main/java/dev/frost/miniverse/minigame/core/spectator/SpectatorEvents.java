package dev.frost.miniverse.minigame.core.spectator;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SpectatorEvents {
    private final List<SpectatorListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(SpectatorListener listener) {
        if (listener != null) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(SpectatorListener listener) {
        this.listeners.remove(listener);
    }

    void notifyStart(SpectatorSession session) {
        for (SpectatorListener listener : this.listeners) {
            listener.onSpectatorStart(session);
        }
    }

    void notifyStop(SpectatorSession session, SpectatorStopReason reason) {
        for (SpectatorListener listener : this.listeners) {
            listener.onSpectatorStop(session, reason);
        }
    }

    void notifyTargetChanged(SpectatorSession session, @Nullable UUID previousTargetId, @Nullable UUID targetId) {
        for (SpectatorListener listener : this.listeners) {
            listener.onSpectatorTargetChanged(session, previousTargetId, targetId);
        }
    }

    void notifyNoTargetElimination(SpectatorSession session) {
        for (SpectatorListener listener : this.listeners) {
            listener.onSpectatorNoTargetElimination(session);
        }
    }

    public interface SpectatorListener {
        void onSpectatorStart(SpectatorSession session);

        void onSpectatorStop(SpectatorSession session, SpectatorStopReason reason);

        void onSpectatorTargetChanged(SpectatorSession session, @Nullable UUID previousTargetId, @Nullable UUID targetId);

        void onSpectatorNoTargetElimination(SpectatorSession session);
    }
}

