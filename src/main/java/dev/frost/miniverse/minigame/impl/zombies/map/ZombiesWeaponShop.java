package dev.frost.miniverse.minigame.impl.zombies.map;

import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponType;
import net.minecraft.util.math.BlockPos;

public class ZombiesWeaponShop {
    private final String id;
    private final BlockPos pos;
    private final WeaponType weaponType;
    private final int purchasePrice;
    private final int refillPrice;

    public ZombiesWeaponShop(String id, BlockPos pos, WeaponType weaponType, int purchasePrice, int refillPrice) {
        this.id = id;
        this.pos = pos;
        this.weaponType = weaponType;
        this.purchasePrice = purchasePrice;
        this.refillPrice = refillPrice;
    }

    public String getId() {
        return this.id;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public WeaponType getWeaponType() {
        return this.weaponType;
    }

    public int getPurchasePrice() {
        return this.purchasePrice;
    }

    public int getRefillPrice() {
        return this.refillPrice;
    }
}
