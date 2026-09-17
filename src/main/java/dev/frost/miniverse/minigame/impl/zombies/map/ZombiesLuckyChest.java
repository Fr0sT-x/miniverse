package dev.frost.miniverse.minigame.impl.zombies.map;

import net.minecraft.util.math.BlockPos;

public class ZombiesLuckyChest {
    private final String id;
    private final BlockPos pos;
    private final int gold;

    public ZombiesLuckyChest(String id, BlockPos pos, int gold) {
        this.id = id;
        this.pos = pos;
        this.gold = gold;
    }

    public String getId() { return this.id; }
    public BlockPos getPos() { return this.pos; }
    public int getGold() { return this.gold; }
}
