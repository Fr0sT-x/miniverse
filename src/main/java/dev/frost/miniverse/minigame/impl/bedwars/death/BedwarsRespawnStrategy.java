package dev.frost.miniverse.minigame.impl.bedwars.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMinigame;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMapConfig;
import dev.frost.miniverse.map.MapPosition;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public final class BedwarsRespawnStrategy implements RespawnStrategy {
    private final BedwarsMinigame minigame;
    private final BedwarsMapConfig mapConfig;

    public BedwarsRespawnStrategy(BedwarsMinigame minigame, BedwarsMapConfig mapConfig) {
        this.minigame = minigame;
        this.mapConfig = mapConfig;
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession spectatorSession) {
        String teamId = context.victimTeamId();
        if (teamId != null) {
            BedwarsMapConfig.BedwarsTeamConfig teamConfig = mapConfig.teams().get(teamId);
            if (teamConfig != null && !teamConfig.spawns.isEmpty()) {
                MapPosition pos = teamConfig.spawns.get(0);
                ServerWorld world = minigame.getContext().nullableServer().getWorld(net.minecraft.world.World.OVERWORLD);
                if (world != null) {
                    return new RespawnLocation(world, new Vec3d(pos.x(), pos.y(), pos.z()), pos.yaw(), pos.pitch());
                }
            }
        }
        
        // Fallback
        ServerWorld world = minigame.getContext().nullableServer().getWorld(net.minecraft.world.World.OVERWORLD);
        if (world == null) {
            world = minigame.getContext().nullableServer().getOverworld();
        }
        net.minecraft.util.math.BlockPos worldSpawn = world.getSpawnPos();
        return new RespawnLocation(world, new Vec3d(worldSpawn.getX() + 0.5, worldSpawn.getY(), worldSpawn.getZ() + 0.5), world.getSpawnAngle(), 0.0f);
    }
}
