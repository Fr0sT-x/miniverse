package dev.frost.miniverse.minigame.core.event;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public interface BlockBreakAware {
    /** Called after a block is successfully broken by a player. State reflects pre-break state. */
    void onBlockBroken(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state);
}
