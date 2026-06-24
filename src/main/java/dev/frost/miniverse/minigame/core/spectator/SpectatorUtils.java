package dev.frost.miniverse.minigame.core.spectator;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class SpectatorUtils {
    private SpectatorUtils() {
    }

    @Nullable
    public static Entity findEntity(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) {
            return null;
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(entityId);
        if (player != null) {
            return player;
        }
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
}

