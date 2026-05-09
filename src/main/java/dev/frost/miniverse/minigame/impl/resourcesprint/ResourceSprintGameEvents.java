package dev.frost.miniverse.minigame.impl.resourcesprint;

import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintMinigame;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ResourceSprintGameEvents {
    private ResourceSprintGameEvents() {
    }

    public static void register() {
        ResourceSprintSessionBootstrap.register();
        ServerTickEvents.END_SERVER_TICK.register(ResourceSprintGameEvents::onServerTick);
        ServerPlayerEvents.AFTER_RESPAWN.register(ResourceSprintGameEvents::onAfterRespawn);
        ServerPlayerEvents.LEAVE.register(ResourceSprintGameEvents::onPlayerLeave);
    }

    private static void onServerTick(net.minecraft.server.MinecraftServer server) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof ResourceSprintMinigame resourceSprint) {
            resourceSprint.onServerTick(server);
        }
    }

    private static void onAfterRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof ResourceSprintMinigame resourceSprint && MinigameManager.getInstance().isParticipant(oldPlayer)) {
            resourceSprint.handlePlayerRespawn(oldPlayer, newPlayer);
        }
    }

    private static void onPlayerLeave(ServerPlayerEntity player) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof ResourceSprintMinigame resourceSprint && MinigameManager.getInstance().isParticipant(player)) {
            resourceSprint.handlePlayerLeave(player);
        }
    }
}



