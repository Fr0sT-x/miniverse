package dev.frost.miniverse.minigame.core.death.policy.impl;

import dev.frost.miniverse.minigame.core.death.CancellationReason;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import net.minecraft.server.network.ServerPlayerEntity;

public class ManualRespawnPolicy implements PostDeathPolicy {
    @Override
    public void start(ServerPlayerEntity victim, DeathContext context) {
        // Remains spectating until external systems manually call execution.
    }

    @Override
    public void cancel(CancellationReason reason) {
        // Nothing to cancel internally
    }
}
