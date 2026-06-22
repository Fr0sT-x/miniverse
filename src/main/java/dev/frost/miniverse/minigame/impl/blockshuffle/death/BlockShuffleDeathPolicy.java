package dev.frost.miniverse.minigame.impl.blockshuffle.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import net.minecraft.server.network.ServerPlayerEntity;

public class BlockShuffleDeathPolicy implements DeathPolicy {
    @Override
    public void execute(ServerPlayerEntity victim, DeathContext context) {
        // Items drop naturally before F05 interception because keepInventory=false
    }

    @Override
    public boolean interceptsRespawn() {
        return true; // Bypass vanilla death screen
    }
}
