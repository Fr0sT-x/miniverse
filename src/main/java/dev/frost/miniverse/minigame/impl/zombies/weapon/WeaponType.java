package dev.frost.miniverse.minigame.impl.zombies.weapon;

import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;

public enum WeaponType {
    KNIFE(WeaponData.builder("Knife", Items.IRON_SWORD)
        .damage(5.0f)
        .melee(true)
        .delay(10)
        .gold(10)
        .build()),

    PISTOL(WeaponData.builder("Pistol", Items.WOODEN_HOE)
        .damage(5.0f)
        .ammo(10, 300)
        .delay(10)
        .reload(30)
        .gold(10)
        .particle(ParticleTypes.CRIT)
        .build()),

    SHOTGUN(WeaponData.builder("Shotgun", Items.IRON_HOE)
        .damage(2.8f)
        .bullets(10, 1.15)
        .ammo(5, 65)
        .delay(22)
        .reload(30)
        .gold(8)
        .pierceLimit(2)
        .particle(ParticleTypes.FLAME)
        .build()),

    RIFLE(WeaponData.builder("Rifle", Items.STONE_HOE)
        .damage(5.0f)
        .ammo(32, 288)
        .delay(4)
        .reload(30)
        .gold(7)
        .particle(ParticleTypes.SMOKE)
        .build()),

    SNIPER(WeaponData.builder("Sniper", Items.WOODEN_SHOVEL)
        .damage(20.0f)
        .bullets(1, 1.0)
        .ammo(4, 40)
        .delay(30)
        .reload(40)
        .gold(30)
        .pierceLimit(3)
        .inChest(true)
        .particle(ParticleTypes.FLAME)
        .build()),

    FLAME_THROWER(WeaponData.builder("Flame Thrower", Items.GOLDEN_HOE)
        .damage(2.0f)
        .bullets(1, 0.95)
        .ammo(50, 350)
        .delay(2)
        .reload(30)
        .gold(4)
        .inChest(true)
        .particle(ParticleTypes.FLAME)
        .build()),

    GOLD_DIGGER(WeaponData.builder("Gold Digger", Items.GOLDEN_PICKAXE)
        .damage(6.0f)
        .ammo(7, 70)
        .delay(10)
        .reload(30)
        .gold(15) // bonus gold!
        .inChest(true)
        .particle(ParticleTypes.ELECTRIC_SPARK)
        .build()),

    ROCKET_LAUNCHER(WeaponData.builder("Rocket Launcher", Items.GOLDEN_SHOVEL)
        .damage(25.0f)
        .ammo(2, 20)
        .delay(30)
        .reload(50)
        .gold(20)
        .inChest(true)
        .particle(ParticleTypes.EXPLOSION)
        .build()),

    ZOMBIE_ZAPPER(WeaponData.builder("Zombie Zapper", Items.DIAMOND_HOE)
        .damage(12.0f)
        .ammo(12, 120)
        .delay(10)
        .reload(30)
        .gold(15)
        .pierceLimit(3)
        .inChest(true)
        .particle(ParticleTypes.ELECTRIC_SPARK)
        .build()),

    // Upgraded (Ultimate Machine) Variants
    LASER_GUN(WeaponData.builder("Laser Gun", Items.WOODEN_HOE)
        .damage(12.0f)
        .ammo(15, 450)
        .delay(8)
        .reload(20)
        .gold(15)
        .pierceLimit(3)
        .particle(ParticleTypes.ELECTRIC_SPARK)
        .build()),

    DOUBLE_BARREL(WeaponData.builder("Double Barrel", Items.IRON_HOE)
        .damage(4.0f)
        .bullets(12, 1.05)
        .ammo(8, 120)
        .delay(18)
        .reload(25)
        .gold(10)
        .pierceLimit(3)
        .particle(ParticleTypes.FLAME)
        .build()),

    ASSAULT_RIFLE(WeaponData.builder("Assault Rifle", Items.STONE_HOE)
        .damage(8.0f)
        .ammo(45, 450)
        .delay(3)
        .reload(25)
        .gold(10)
        .particle(ParticleTypes.CRIT)
        .build()),

    RAILGUN(WeaponData.builder("Railgun", Items.WOODEN_SHOVEL)
        .damage(50.0f)
        .ammo(6, 60)
        .delay(25)
        .reload(30)
        .gold(40)
        .pierceLimit(5)
        .particle(ParticleTypes.FIREWORK)
        .build()),

    NUKE_LAUNCHER(WeaponData.builder("Nuke Launcher", Items.GOLDEN_SHOVEL)
        .damage(60.0f)
        .ammo(4, 40)
        .delay(30)
        .reload(45)
        .gold(30)
        .particle(ParticleTypes.EXPLOSION)
        .build()),

    TESLA_GUN(WeaponData.builder("Tesla Gun", Items.DIAMOND_HOE)
        .damage(24.0f)
        .ammo(18, 180)
        .delay(12)
        .reload(25)
        .gold(20)
        .pierceLimit(3)
        .particle(ParticleTypes.ELECTRIC_SPARK)
        .build());

    private final WeaponData data;

    WeaponType(WeaponData data) {
        this.data = data;
    }

    public WeaponData getData() {
        return this.data;
    }

    public WeaponType getUpgradedVersion() {
        return switch (this) {
            case PISTOL -> LASER_GUN;
            case SHOTGUN -> DOUBLE_BARREL;
            case RIFLE -> ASSAULT_RIFLE;
            case SNIPER -> RAILGUN;
            case ROCKET_LAUNCHER -> NUKE_LAUNCHER;
            case ZOMBIE_ZAPPER -> TESLA_GUN;
            default -> null;
        };
    }

    public boolean isUpgraded() {
        return this == LASER_GUN || this == DOUBLE_BARREL || this == ASSAULT_RIFLE || this == RAILGUN || this == NUKE_LAUNCHER || this == TESLA_GUN;
    }

    public static WeaponType fromString(String name) {
        if (name == null) return PISTOL;
        try {
            return WeaponType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PISTOL;
        }
    }
}
