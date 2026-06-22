package dev.frost.miniverse.minigame.impl.bridge.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.impl.bridge.BridgeMinigame;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.world.GameMode;

public class BridgeDeathCallbacks implements DeathLifecycleCallbacks {
    private final BridgeMinigame minigame;

    public BridgeDeathCallbacks(BridgeMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public void onDeath(ServerPlayerEntity player, DeathContext context) {
        // Nothing to do on death
    }

    @Override
    public void onRespawnComplete(ServerPlayerEntity player, DeathContext context) {
        if (this.minigame.getState() == dev.frost.miniverse.minigame.core.GameState.RUNNING) {
            player.changeGameMode(GameMode.SURVIVAL);
            this.minigame.applyKit(player);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 3 * 20, 255, false, false, true));
        }
    }
}
