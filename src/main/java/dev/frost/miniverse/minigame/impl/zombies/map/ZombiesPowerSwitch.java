package dev.frost.miniverse.minigame.impl.zombies.map;

import net.minecraft.util.math.BlockPos;

public class ZombiesPowerSwitch {
    private final String id;
    private final BlockPos pos;
    private final int gold;
    private boolean active;

    public ZombiesPowerSwitch(String id, BlockPos pos, int gold) {
        this.id = id;
        this.pos = pos;
        this.gold = gold;
        this.active = false;
    }

    public String getId() { return this.id; }
    public BlockPos getPos() { return this.pos; }
    public int getGold() { return this.gold; }
    public boolean isActive() { return this.active; }
    public void setActive(boolean active) { this.active = active; }
}
