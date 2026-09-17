package dev.frost.miniverse.minigame.impl.zombies.map;

import net.minecraft.util.math.BlockPos;

public class ZombiesTeamMachine {
    private final String id;
    private final BlockPos pos;

    public ZombiesTeamMachine(String id, BlockPos pos) {
        this.id = id;
        this.pos = pos;
    }

    public String getId() { return this.id; }
    public BlockPos getPos() { return this.pos; }
}
