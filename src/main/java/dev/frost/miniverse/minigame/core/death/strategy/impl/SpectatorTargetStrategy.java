package dev.frost.miniverse.minigame.core.death.strategy.impl;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class SpectatorTargetStrategy implements RespawnStrategy {
    private final MinecraftServer server;
    private final RespawnStrategy fallback;

    public SpectatorTargetStrategy(MinecraftServer server, RespawnStrategy fallback) {
        this.server = Objects.requireNonNull(server, "server cannot be null");
        this.fallback = Objects.requireNonNull(fallback, "fallback cannot be null");
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession spectatorSession) {
        if (spectatorSession == null) {
            return this.fallback.resolve(context, spectatorSession);
        }

        java.util.UUID targetId = spectatorSession.targetId();
        if (targetId == null) {
            return this.fallback.resolve(context, spectatorSession);
        }

        net.minecraft.server.network.ServerPlayerEntity target = this.server.getPlayerManager().getPlayer(targetId);
        if (target == null) {
            return this.fallback.resolve(context, spectatorSession);
        }

        ServerWorld world = target.getServerWorld();
        return new RespawnLocation(world, target.getPos(), target.getYaw(), target.getPitch());
    }
}
