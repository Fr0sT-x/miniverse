package dev.frost.miniverse.minigame.core.death.policy.impl;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import net.minecraft.server.network.ServerPlayerEntity;

// Recommended to set doImmediateRespawn=false under the initialize method, as this policy uses the vanilla respawn system.

public class VanillaDeathPolicy implements DeathPolicy {
    @Override
    public boolean interceptsRespawn() {
        return false;
    }

    @Override
    public void execute(ServerPlayerEntity victim, DeathContext context) {
        // Vanilla death handles inventory drops, health, and respawning screens natively.
        // The framework intercepts events but does not alter the core Minecraft death behavior.
    }
}
