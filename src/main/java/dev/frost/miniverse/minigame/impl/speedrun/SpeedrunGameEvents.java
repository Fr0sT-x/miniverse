package dev.frost.miniverse.minigame.impl.speedrun;

import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side hooks for the Speedrun minigame.
 */
public final class SpeedrunGameEvents {
    private SpeedrunGameEvents() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SpeedrunGameEvents::onServerTick);
        ServerLivingEntityEvents.AFTER_DEATH.register(SpeedrunGameEvents::onAfterDeath);
        ServerPlayerEvents.AFTER_RESPAWN.register(SpeedrunGameEvents::onAfterRespawn);
        ServerPlayerEvents.LEAVE.register(SpeedrunGameEvents::onPlayerLeave);
    }

    private static void onServerTick(net.minecraft.server.MinecraftServer server) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof SpeedrunMinigame speedrun) {
            speedrun.onServerTick(server);
        }
    }

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (!(active instanceof SpeedrunMinigame speedrun)) {
            return;
        }

        if (entity instanceof EnderDragonEntity) {
            speedrun.handleDragonDeath();
            return;
        }

        if (entity instanceof ServerPlayerEntity player && MinigameManager.getInstance().isParticipant(player)) {
            speedrun.onPlayerDeath(player);
        }
    }

    private static void onAfterRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof SpeedrunMinigame speedrun && MinigameManager.getInstance().isParticipant(oldPlayer)) {
            speedrun.handlePlayerRespawn(oldPlayer, newPlayer);
        }
    }

    private static void onPlayerLeave(ServerPlayerEntity player) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof SpeedrunMinigame speedrun && MinigameManager.getInstance().isParticipant(player)) {
            speedrun.handlePlayerLeave(player);
        }
    }
}


