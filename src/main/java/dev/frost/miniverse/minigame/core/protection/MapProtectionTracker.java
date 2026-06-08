package dev.frost.miniverse.minigame.core.protection;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks blocks that are placed dynamically during a session.
 * Used to distinguish breakable blocks from protected map blocks.
 */
public final class MapProtectionTracker {
    private final Set<BlockPos> placedBlocks = new HashSet<>();

    public void addPlacedBlock(BlockPos pos) {
        this.placedBlocks.add(pos.toImmutable());
    }

    public void removePlacedBlock(BlockPos pos) {
        this.placedBlocks.remove(pos);
    }

    public boolean isPlacedBlock(BlockPos pos) {
        return this.placedBlocks.contains(pos);
    }

    public void clear() {
        this.placedBlocks.clear();
    }
}
