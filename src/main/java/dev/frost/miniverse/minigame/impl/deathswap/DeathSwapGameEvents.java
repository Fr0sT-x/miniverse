package dev.frost.miniverse.minigame.impl.deathswap;

import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapSessionBootstrap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DeathSwapGameEvents {
    private DeathSwapGameEvents() {
    }

    public static void register() {
        DeathSwapSessionBootstrap.register();
        ServerTickEvents.END_SERVER_TICK.register(DeathSwapGameEvents::onServerTick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(DeathSwapGameEvents::onAllowDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(DeathSwapGameEvents::onAfterDeath);
        ServerPlayerEvents.AFTER_RESPAWN.register(DeathSwapGameEvents::onAfterRespawn);
        ServerPlayerEvents.LEAVE.register(DeathSwapGameEvents::onPlayerLeave);
    }

    private static void onServerTick(net.minecraft.server.MinecraftServer server) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof DeathSwapMinigame deathSwap) {
            deathSwap.onServerTick(server);
        }
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }

        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof DeathSwapMinigame deathSwap && MinigameManager.getInstance().isParticipant(player)) {
            return !deathSwap.shouldCancelDamage(player);
        }

        return true;
    }

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }

        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof DeathSwapMinigame deathSwap && MinigameManager.getInstance().isParticipant(player)) {
            deathSwap.onPlayerDeath(player);
        }
    }

    private static void onAfterRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof DeathSwapMinigame deathSwap && MinigameManager.getInstance().isParticipant(oldPlayer)) {
            deathSwap.handlePlayerRespawn(oldPlayer, newPlayer);
        }
    }

    private static void onPlayerLeave(ServerPlayerEntity player) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof DeathSwapMinigame deathSwap && MinigameManager.getInstance().isParticipant(player)) {
            deathSwap.handlePlayerLeave(player);
        }
    }
}


