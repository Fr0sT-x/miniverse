package dev.frost.miniverse.minigame.impl.bridge.death;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.impl.bridge.BridgeMinigame;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BridgeRespawnStrategy implements RespawnStrategy {
    private final BridgeMinigame minigame;

    public BridgeRespawnStrategy(BridgeMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession currentSession) {
        ServerWorld world = minigame.getContext() != null && minigame.getContext().nullableServer() != null
            ? minigame.getContext().nullableServer().getWorld(context.dimension())
            : null;

        if (world == null) {
            return null;
        }

        String teamId = minigame.getTeamId(context.victimId());
        List<MapPosition> spawns = BridgeMinigame.RED_TEAM.equals(teamId) ? minigame.getMapConfig().redSpawns()
            : BridgeMinigame.BLUE_TEAM.equals(teamId) ? minigame.getMapConfig().blueSpawns()
            : List.of();

        if (spawns.isEmpty()) {
            return null;
        }

        MapPosition spawn = spawns.get(Math.floorMod(context.victimId().hashCode(), spawns.size()));
        return new RespawnLocation(world, new Vec3d(spawn.x(), spawn.y(), spawn.z()), spawn.yaw(), spawn.pitch());
    }
}
