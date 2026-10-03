package dev.frost.miniverse.minigame.impl.microparty;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks temporary block changes during micro-challenges so the arena
 * platform is always 100% restored when a round or game ends.
 */
public final class TemporaryBlockManager {
    private final Map<BlockPos, BlockState> originalStates = new HashMap<>();

    public void setTemporaryBlock(ServerWorld world, BlockPos pos, BlockState newState) {
        if (world == null || pos == null || newState == null) {
            return;
        }
        BlockPos immutable = pos.toImmutable();
        if (!this.originalStates.containsKey(immutable)) {
            this.originalStates.put(immutable, world.getBlockState(immutable));
        }
        world.setBlockState(immutable, newState, Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
    }

    public void restoreAll(ServerWorld world) {
        if (world == null || this.originalStates.isEmpty()) {
            this.originalStates.clear();
            return;
        }
        for (Map.Entry<BlockPos, BlockState> entry : this.originalStates.entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
        }
        this.originalStates.clear();
    }

    public boolean hasTrackedBlocks() {
        return !this.originalStates.isEmpty();
    }

    public int trackedBlockCount() {
        return this.originalStates.size();
    }
}
