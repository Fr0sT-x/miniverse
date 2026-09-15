package dev.frost.miniverse.minigame.core.protection;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

@FunctionalInterface
public interface BlockProtectionProvider {
    /**
     * Returns true if the block at the given position is protected from breaking and explosions.
     */
    boolean isBlockProtected(ServerWorld world, BlockPos pos);
}
