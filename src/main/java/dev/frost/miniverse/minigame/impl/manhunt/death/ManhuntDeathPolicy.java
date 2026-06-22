package dev.frost.miniverse.minigame.impl.manhunt.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import net.minecraft.server.network.ServerPlayerEntity;

public class ManhuntDeathPolicy implements DeathPolicy {
    @Override
    public void execute(ServerPlayerEntity player, DeathContext context) {
        // Items drop naturally due to keepInventory=false.
        // We just intercept respawn.
    }

    @Override
    public boolean interceptsRespawn() {
        return true;
    }
}
