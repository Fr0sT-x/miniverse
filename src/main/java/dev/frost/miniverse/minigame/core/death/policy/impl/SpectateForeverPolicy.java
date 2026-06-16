package dev.frost.miniverse.minigame.core.death.policy.impl;

import dev.frost.miniverse.minigame.core.death.CancellationReason;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import net.minecraft.server.network.ServerPlayerEntity;

public class SpectateForeverPolicy implements PostDeathPolicy {
    @Override
    public void start(ServerPlayerEntity victim, DeathContext context) {
        // Does nothing. Player remains in spectating mode forever until the match ends.
    }

    @Override
    public void cancel(CancellationReason reason) {
        // Nothing to cancel
    }
}
