package dev.frost.miniverse.minigame.core.event;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public interface BlockBreakBypassAware {
    /**
     * Determines whether a player can bypass the global map protection to break a block.
     * Return true to allow the block break, ignoring map protection.
     */
    boolean canBypassProtection(ServerPlayerEntity player, BlockPos pos);
}
