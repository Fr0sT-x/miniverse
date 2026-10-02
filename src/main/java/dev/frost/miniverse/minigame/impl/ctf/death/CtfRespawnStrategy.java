package dev.frost.miniverse.minigame.impl.ctf.death;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagMapConfig;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagMinigame;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public final class CtfRespawnStrategy implements RespawnStrategy {
    private final CaptureTheFlagMinigame minigame;
    private final CaptureTheFlagMapConfig mapConfig;
    private final Random random = new Random();

    public CtfRespawnStrategy(CaptureTheFlagMinigame minigame, CaptureTheFlagMapConfig mapConfig) {
        this.minigame = minigame;
        this.mapConfig = mapConfig;
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession spectatorSession) {
        String teamId = context.victimTeamId();
        if (teamId != null) {
            CaptureTheFlagMapConfig.CtfTeamConfig teamConfig = mapConfig.teams().get(teamId);
            if (teamConfig != null && !teamConfig.spawns.isEmpty()) {
                MapPosition pos = teamConfig.spawns.get(random.nextInt(teamConfig.spawns.size()));
                ServerWorld world = minigame.getContext().nullableServer().getOverworld();
                return new RespawnLocation(world, new Vec3d(pos.x(), pos.y(), pos.z()), pos.yaw(), pos.pitch());
            }
        }

        ServerWorld world = minigame.getContext().nullableServer().getOverworld();
        BlockPos spawn = world.getSpawnPos();
        return new RespawnLocation(world, new Vec3d(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5), world.getSpawnAngle(), 0.0F);
    }
}
