package dev.frost.miniverse.minigame.impl.microparty;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.editor.RegionPart;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility helper to determine arena floor coordinates, bounds,
 * and walkable platform positions for procedural microgames.
 */
public final class MicroPartyArenaHelper {
    private MicroPartyArenaHelper() {}

    public static int getFloorY(MicroPartyMapConfig config) {
        if (config == null) {
            return 100;
        }
        if (config.arenaCenter() != null) {
            return (int) Math.floor(config.arenaCenter().y());
        }
        if (!config.playerSpawns().isEmpty()) {
            return (int) Math.floor(config.playerSpawns().get(0).y());
        }
        if (!config.arenaBounds().isEmpty()) {
            return (int) Math.floor(config.arenaBounds().get(0).min().y()) + 1;
        }
        return 100;
    }

    public static record ArenaBounds2D(int minX, int maxX, int minZ, int maxZ) {
        public int width() { return maxX - minX + 1; }
        public int depth() { return maxZ - minZ + 1; }
        public int centerX() { return (minX + maxX) / 2; }
        public int centerZ() { return (minZ + maxZ) / 2; }
    }

    public static ArenaBounds2D getBounds2D(MicroPartyMapConfig config) {
        if (config != null && !config.arenaBounds().isEmpty()) {
            RegionPart first = config.arenaBounds().get(0);
            int minX = (int) Math.floor(Math.min(first.min().x(), first.max().x()));
            int maxX = (int) Math.floor(Math.max(first.min().x(), first.max().x()));
            int minZ = (int) Math.floor(Math.min(first.min().z(), first.max().z()));
            int maxZ = (int) Math.floor(Math.max(first.min().z(), first.max().z()));
            for (int i = 1; i < config.arenaBounds().size(); i++) {
                RegionPart r = config.arenaBounds().get(i);
                minX = Math.min(minX, (int) Math.floor(Math.min(r.min().x(), r.max().x())));
                maxX = Math.max(maxX, (int) Math.floor(Math.max(r.min().x(), r.max().x())));
                minZ = Math.min(minZ, (int) Math.floor(Math.min(r.min().z(), r.max().z())));
                maxZ = Math.max(maxZ, (int) Math.floor(Math.max(r.min().z(), r.max().z())));
            }
            return new ArenaBounds2D(minX, maxX, minZ, maxZ);
        }

        if (config != null && config.arenaCenter() != null) {
            int cx = (int) Math.floor(config.arenaCenter().x());
            int cz = (int) Math.floor(config.arenaCenter().z());
            return new ArenaBounds2D(cx - 12, cx + 12, cz - 12, cz + 12);
        }

        return new ArenaBounds2D(-12, 12, -12, 12);
    }

    public static List<BlockPos> getWalkableFloorSurface(ServerWorld world, MicroPartyMapConfig config) {
        List<BlockPos> surface = new ArrayList<>();
        if (world == null || config == null) {
            return surface;
        }

        int floorY = getFloorY(config);
        int surfaceY = floorY - 1;
        ArenaBounds2D bounds = getBounds2D(config);

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                BlockPos floorPos = new BlockPos(x, surfaceY, z);
                BlockPos airPos = new BlockPos(x, floorY, z);
                if (!world.getBlockState(floorPos).isAir() && world.getBlockState(airPos).isAir()) {
                    surface.add(floorPos);
                }
            }
        }
        return surface;
    }
}
