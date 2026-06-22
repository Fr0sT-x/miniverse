package dev.frost.miniverse.minigame.impl.resourcesprint.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import net.minecraft.server.network.ServerPlayerEntity;

public class ResourceSprintDeathCallbacks implements DeathLifecycleCallbacks {

    @Override
    public void onDeath(ServerPlayerEntity victim, DeathContext context) {
    }

    @Override
    public void onRespawnComplete(ServerPlayerEntity player, DeathContext context) {
    }
}
