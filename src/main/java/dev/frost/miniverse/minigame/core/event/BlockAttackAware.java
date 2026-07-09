package dev.frost.miniverse.minigame.core.event;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Indicates that this minigame listens to block attack (left-click) events.
 */
public interface BlockAttackAware {
    ActionResult onAttackBlock(ServerPlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction);
}
