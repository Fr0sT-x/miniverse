package dev.frost.miniverse.minigame.core.death.strategy.impl;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class DeathLocationStrategy implements RespawnStrategy {
    private final MinecraftServer server;

    public DeathLocationStrategy(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server cannot be null");
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession spectatorSession) {
        ServerWorld world = this.server.getWorld(context.dimension());
        if (world == null) {
            throw new IllegalStateException("Failed to resolve death location: Dimension " + context.dimension().getValue() + " not found on server.");
        }

        return new RespawnLocation(world, context.location(), context.yawAtDeath(), context.pitchAtDeath());
    }
}
