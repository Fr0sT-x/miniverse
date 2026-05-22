package dev.frost.miniverse.minigame.core.lifecycle;

import dev.frost.miniverse.minigame.core.MinigameRuntime;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.UUID;

public interface DisconnectGraceHandler {
    boolean isCritical(MinigameRuntime runtime, ServerPlayerEntity player);

    void onGraceExpired(MinigameRuntime runtime, List<UUID> pendingPlayers);

    default void onGraceStarted(MinigameRuntime runtime, List<UUID> pendingPlayers, int graceSeconds) {
    }

    default void onGraceCancelled(MinigameRuntime runtime) {
    }
}

