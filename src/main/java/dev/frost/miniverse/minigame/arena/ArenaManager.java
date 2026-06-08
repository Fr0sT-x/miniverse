package dev.frost.miniverse.minigame.arena;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ArenaManager {

    private final ServerWorld world;
    private final List<Arena> arenas = new ArrayList<>();

    public ArenaManager(ServerWorld world) {
        this.world = world;
    }

    public void registerArena(ArenaRegion region) {
        arenas.add(new Arena(world, region));
    }

    public List<Arena> getArenas() {
        return Collections.unmodifiableList(arenas);
    }

    public Optional<Arena> findIdleArena() {
        return arenas.stream()
            .filter(a -> a.getState() == ArenaState.IDLE)
            .findFirst();
    }

    public Optional<Arena> getArenaAt(BlockPos pos) {
        return arenas.stream()
            .filter(a -> a.contains(pos))
            .findFirst();
    }

    /**
     * Called via Mixin whenever a block is modified in the world.
     */
    public void onBlockChanged(BlockPos pos, BlockState oldState, BlockEntity oldEntity) {
        getArenaAt(pos).ifPresent(arena -> {
            if (arena.getState() == ArenaState.RUNNING) {
                arena.trackBlockChange(pos, oldState, oldEntity);
            }
        });
    }

    /**
     * Utility for programmatically modifying blocks safely inside an arena while tracking it.
     */
    public void setBlock(BlockPos pos, BlockState state) {
        getArenaAt(pos).ifPresent(arena -> {
            if (arena.getState() == ArenaState.RUNNING) {
                BlockState oldState = world.getBlockState(pos);
                BlockEntity oldEntity = world.getBlockEntity(pos);
                arena.trackBlockChange(pos, oldState, oldEntity);
            }
        });
        world.setBlockState(pos, state);
    }
}
