package dev.frost.miniverse.minigame.impl.zombies.map;

import dev.frost.miniverse.minigame.impl.zombies.perk.PlayerPerk;
import net.minecraft.util.math.BlockPos;

public class ZombiesPerkMachine {
    private final String id;
    private final BlockPos pos;
    private final PlayerPerk perk;
    private final int gold;

    public ZombiesPerkMachine(String id, BlockPos pos, PlayerPerk perk, int gold) {
        this.id = id;
        this.pos = pos;
        this.perk = perk;
        this.gold = gold;
    }

    public String getId() { return this.id; }
    public BlockPos getPos() { return this.pos; }
    public PlayerPerk getPerk() { return this.perk; }
    public int getGold() { return this.gold; }
}
