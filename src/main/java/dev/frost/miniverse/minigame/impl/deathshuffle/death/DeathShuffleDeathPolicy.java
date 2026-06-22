package dev.frost.miniverse.minigame.impl.deathshuffle.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import net.minecraft.server.network.ServerPlayerEntity;

public class DeathShuffleDeathPolicy implements DeathPolicy {
    @Override
    public void execute(ServerPlayerEntity victim, DeathContext context) {
        // Items drop naturally before F05 interception if KEEP_INVENTORY is false.
        // In DeathShuffle, KEEP_INVENTORY is usually true, so items are retained automatically.
    }

    @Override
    public boolean interceptsRespawn() {
        return true; // Bypass vanilla death screen
    }
}
