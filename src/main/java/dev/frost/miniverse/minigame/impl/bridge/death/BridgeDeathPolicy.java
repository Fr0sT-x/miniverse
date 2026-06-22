package dev.frost.miniverse.minigame.impl.bridge.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import net.minecraft.server.network.ServerPlayerEntity;

public class BridgeDeathPolicy implements DeathPolicy {
    @Override
    public void execute(ServerPlayerEntity player, DeathContext context) {
        // No kill/death tracking is implemented in Bridge currently.
    }

    @Override
    public boolean interceptsRespawn() {
        return true;
    }
}
