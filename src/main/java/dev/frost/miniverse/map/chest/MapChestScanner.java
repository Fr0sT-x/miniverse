package dev.frost.miniverse.map.chest;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.editor.MapMarker;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.MarkerType;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * High-performance container scanner and classifier for minigame maps.
 * Automatically scans chunks for single/double chests and barrels,
 * classifying them into Island/Spawn chests and Mid/Center Feast chests.
 */
public final class MapChestScanner {
    public static final double DEFAULT_MID_RADIUS = 22.0;
    public static final double DEFAULT_ISLAND_RADIUS = 16.0;
    public static final int DEFAULT_SEARCH_RADIUS = 250;

    private MapChestScanner() {
    }

    public record ScanResult(
        List<BlockPos> islandChests,
        List<BlockPos> midChests,
        int totalFound
    ) {
        public ScanResult {
            islandChests = Collections.unmodifiableList(new ArrayList<>(islandChests));
            midChests = Collections.unmodifiableList(new ArrayList<>(midChests));
        }
    }

    /**
     * Scans the world around the given center point, finds all container entities,
     * and categorizes them into Island chests and Mid chests based on spawn proximity or center radius.
     */
    public static ScanResult scan(
        ServerWorld world,
        BlockPos centerPos,
        int searchRadiusBlocks,
        double midRadiusBlocks,
        double islandRadiusBlocks,
        List<MapPosition> islandSpawns
    ) {
        if (world == null) {
            return new ScanResult(List.of(), List.of(), 0);
        }

        BlockPos center = centerPos != null ? centerPos : BlockPos.ORIGIN;
        int minChunkX = (center.getX() - searchRadiusBlocks) >> 4;
        int maxChunkX = (center.getX() + searchRadiusBlocks) >> 4;
        int minChunkZ = (center.getZ() - searchRadiusBlocks) >> 4;
        int maxChunkZ = (center.getZ() + searchRadiusBlocks) >> 4;

        Set<BlockPos> detectedChests = new HashSet<>();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                Chunk chunk = world.getChunk(cx, cz);
                if (chunk instanceof WorldChunk worldChunk) {
                    Set<BlockPos> positions = new HashSet<>(worldChunk.getBlockEntityPositions());
                    positions.addAll(worldChunk.getBlockEntities().keySet());
                    for (BlockPos pos : positions) {
                        BlockEntity be = world.getBlockEntity(pos);
                        if (be != null && isContainerEntity(be)) {
                            detectedChests.add(pos.toImmutable());
                        }
                    }
                }
            }
        }

        List<BlockPos> islandChests = new ArrayList<>();
        List<BlockPos> midChests = new ArrayList<>();

        boolean hasSpawns = islandSpawns != null && !islandSpawns.isEmpty();

        for (BlockPos pos : detectedChests) {
            if (hasSpawns) {
                // If island spawn markers are placed, classify based on proximity to nearest spawn
                double minDistToSpawnSq = Double.MAX_VALUE;
                for (MapPosition spawn : islandSpawns) {
                    double dx = pos.getX() + 0.5 - spawn.x();
                    double dz = pos.getZ() + 0.5 - spawn.z();
                    double distSq = dx * dx + dz * dz;
                    if (distSq < minDistToSpawnSq) {
                        minDistToSpawnSq = distSq;
                    }
                }

                if (minDistToSpawnSq <= islandRadiusBlocks * islandRadiusBlocks) {
                    islandChests.add(pos);
                } else {
                    midChests.add(pos);
                }
            } else {
                // Otherwise classify by radial distance from center
                double dx = pos.getX() + 0.5 - center.getX();
                double dz = pos.getZ() + 0.5 - center.getZ();
                double distFromCenterSq = dx * dx + dz * dz;

                if (distFromCenterSq <= midRadiusBlocks * midRadiusBlocks) {
                    midChests.add(pos);
                } else {
                    islandChests.add(pos);
                }
            }
        }

        // Sort positions deterministically for clean display and consistency
        Comparator<BlockPos> comparator = Comparator
            .comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getZ);

        islandChests.sort(comparator);
        midChests.sort(comparator);

        return new ScanResult(islandChests, midChests, detectedChests.size());
    }

    private static boolean isContainerEntity(BlockEntity be) {
        return be instanceof ChestBlockEntity
            || be instanceof TrappedChestBlockEntity
            || be instanceof BarrelBlockEntity;
    }

    /**
     * Converts a list of scanned block positions into editor MapMarkers.
     */
    public static List<MapMarker> toMarkers(MarkerDefinition definition, List<BlockPos> positions) {
        if (definition == null || positions == null) {
            return List.of();
        }
        List<MapMarker> markers = new ArrayList<>();
        int index = 1;
        for (BlockPos pos : positions) {
            MapPosition mapPos = MapPosition.of(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            markers.add(new MapMarker(
                UUID.randomUUID().toString(),
                definition.key(),
                definition.displayName() + " #" + index++,
                MarkerType.POINT,
                List.of(mapPos),
                List.of(),
                new com.google.gson.JsonObject()
            ));
        }
        return markers;
    }
}
