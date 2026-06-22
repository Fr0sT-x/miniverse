package dev.frost.miniverse.minigame.impl.speedrun.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import net.minecraft.server.network.ServerPlayerEntity;

public class SpeedrunDeathCallbacks implements DeathLifecycleCallbacks {
    @Override
    public void onDeathProcessed(ServerPlayerEntity player, DeathContext context) {
        // TODO: Add death counter/sound hook
    }
}
