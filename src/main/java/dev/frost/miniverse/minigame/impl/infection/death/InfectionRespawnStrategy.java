package dev.frost.miniverse.minigame.impl.infection.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.impl.infection.InfectionMinigame;
import net.minecraft.server.network.ServerPlayerEntity;

public class InfectionRespawnStrategy implements RespawnStrategy {
    private final InfectionMinigame minigame;

    public InfectionRespawnStrategy(InfectionMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @org.jetbrains.annotations.Nullable SpectatorSession currentSession) {
        ServerPlayerEntity player = this.minigame.context().resolvePlayer(context.victimId()).orElse(null);
        if (player == null) {
            return null;
        }
        dev.frost.miniverse.map.MapPosition spawn = this.minigame.getRandomSpawn(player);
        if (spawn != null) {
            return new RespawnLocation(player.getServerWorld(), new net.minecraft.util.math.Vec3d(spawn.x(), spawn.y(), spawn.z()), spawn.yaw(), spawn.pitch());
        }
        net.minecraft.util.math.BlockPos worldSpawn = player.getServerWorld().getSpawnPos();
        return new RespawnLocation(player.getServerWorld(), new net.minecraft.util.math.Vec3d(worldSpawn.getX() + 0.5, worldSpawn.getY(), worldSpawn.getZ() + 0.5), player.getServerWorld().getSpawnAngle(), 0.0f);
    }
}
