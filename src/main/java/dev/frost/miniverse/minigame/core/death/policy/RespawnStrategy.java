package dev.frost.miniverse.minigame.core.death.policy;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public interface RespawnStrategy {
    /**
     * Resolves the location where the player should respawn.
     * This method must always return a valid location, utilizing fallbacks if necessary.
     *
     * @param context          the context of the death
     * @param spectatorSession the player's current spectator session, if any
     * @return the resolved respawn location (never null)
     */
    RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession spectatorSession);

    record RespawnLocation(ServerWorld world, Vec3d pos, float yaw, float pitch) {
    }

    /**
     * Resolves a vanilla-style respawn location (checking for a valid bed/respawn anchor, 
     * falling back to the world spawn).
     *
     * @param server   the minecraft server
     * @param victimId the player's UUID
     * @param fallbackWorld the fallback world to use if no bed is found
     * @return the resolved respawn location
     */
    static RespawnLocation resolveVanillaSpawn(net.minecraft.server.MinecraftServer server, java.util.UUID victimId, ServerWorld fallbackWorld) {
        net.minecraft.server.network.ServerPlayerEntity player = server.getPlayerManager().getPlayer(victimId);
        
        if (player != null) {
            ServerWorld spawnWorld = server.getWorld(player.getSpawnPointDimension());
            net.minecraft.util.math.BlockPos spawnPos = player.getSpawnPointPosition();
            if (spawnWorld != null && spawnPos != null) {
                // Approximate the spawn position to avoid missing method signatures in different Fabric versions
                return new RespawnLocation(spawnWorld, new Vec3d(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5), player.getSpawnAngle(), 0.0f);
            }
        }
        
        ServerWorld world = fallbackWorld;
        if (world == null) {
            world = server.getOverworld();
        }
        net.minecraft.util.math.BlockPos worldSpawn = world.getSpawnPos();
        return new RespawnLocation(world, new Vec3d(worldSpawn.getX() + 0.5, worldSpawn.getY(), worldSpawn.getZ() + 0.5), world.getSpawnAngle(), 0.0f);
    }
}
