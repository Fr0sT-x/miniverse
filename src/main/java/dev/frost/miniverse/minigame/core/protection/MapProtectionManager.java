package dev.frost.miniverse.minigame.core.protection;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.session.BackendLaunchMode;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MapProtectionManager {
    private MapProtectionManager() {
    }

    /**
     * Checks if the block at the given position is protected.
     * Anything that exists prior to the session and wasn't placed dynamically is protected.
     */
    public static boolean isProtected(ServerWorld world, BlockPos pos) {
        if (SessionRuntimeConfig.getLaunchMode() == BackendLaunchMode.MAP_EDITOR) {
            return false;
        }

        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime == null || !runtime.state().isActive()) {
            return false;
        }

        String gameId = System.getProperty("miniverse.session.game", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (!gameId.isEmpty() && dev.frost.miniverse.map.MapGamemodeRegistry.get(gameId).isEmpty()) {
            return false;
        }

        return !runtime.context().protectionTracker().isPlacedBlock(pos);
    }

    /**
     * Checks if a player is allowed to break the block.
     * If they cannot, optionally sends a message to the player.
     */
    public static boolean canBreak(ServerPlayerEntity player, BlockPos pos, boolean sendMessage) {
        if (player.isCreative() || player.hasPermissionLevel(2)) {
            // Creative/OP players bypass if needed, though typically gamemodes handle this.
            // But per requirements, everything is protected unless tracked.
        }
        
        if (!isProtected((ServerWorld) player.getWorld(), pos)) {
            return true;
        }

        if (sendMessage) {
            player.sendMessage(Text.literal("You cannot break that block.").formatted(Formatting.RED), false);
        }
        return false;
    }

    /**
     * Called when a block is successfully broken to clean it from the tracker.
     */
    public static void onBlockBroken(BlockPos pos) {
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            runtime.context().protectionTracker().removePlacedBlock(pos);
        }
    }
}
