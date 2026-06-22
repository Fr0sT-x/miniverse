package dev.frost.miniverse.minigame.impl.bountyhunt.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntMinigame;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class BountyHuntDeathCallbacks implements DeathLifecycleCallbacks {
    private final BountyHuntMinigame minigame;

    public BountyHuntDeathCallbacks(BountyHuntMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public void onDeath(ServerPlayerEntity player, DeathContext context) {
        this.minigame.processPlayerDeath(player, context.damageSource());
    }

    @Override
    public void onRespawnComplete(ServerPlayerEntity player, DeathContext context) {
        this.minigame.processPlayerRespawn(player);
    }
}
