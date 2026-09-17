package dev.frost.miniverse.minigame.impl.zombies.weapon;

import net.minecraft.item.Item;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

public record WeaponData(
    String displayName,
    Item item,
    float damage,
    int bulletsPerShot,
    double spread,
    int delayTicks,
    int reloadTicks,
    int clipSize,
    int maxReserve,
    int goldPerHit,
    boolean isMelee,
    boolean isPiercing,
    int pierceLimit,
    boolean inLuckyChest,
    ParticleEffect bulletParticle
) {
    public static Builder builder(String name, Item item) {
        return new Builder(name, item);
    }

    public static class Builder {
        private final String name;
        private final Item item;
        private float damage = 5.0f;
        private int bulletsPerShot = 1;
        private double spread = 1.0;
        private int delayTicks = 10;
        private int reloadTicks = 30;
        private int clipSize = 10;
        private int maxReserve = 300;
        private int goldPerHit = 10;
        private boolean melee = false;
        private boolean piercing = false;
        private int pierceLimit = 1;
        private boolean inChest = false;
        private ParticleEffect particle = ParticleTypes.FLAME;

        public Builder(String name, Item item) {
            this.name = name;
            this.item = item;
        }

        public Builder damage(float damage) { this.damage = damage; return this; }
        public Builder bullets(int bullets, double spread) { this.bulletsPerShot = bullets; this.spread = spread; return this; }
        public Builder delay(int ticks) { this.delayTicks = ticks; return this; }
        public Builder reload(int ticks) { this.reloadTicks = ticks; return this; }
        public Builder ammo(int clip, int reserve) { this.clipSize = clip; this.maxReserve = reserve; return this; }
        public Builder gold(int perHit) { this.goldPerHit = perHit; return this; }
        public Builder melee(boolean melee) { this.melee = melee; return this; }
        public Builder piercing(boolean piercing) {
            this.piercing = piercing;
            if (piercing && this.pierceLimit <= 1) this.pierceLimit = 3;
            return this;
        }
        public Builder pierceLimit(int limit) {
            this.pierceLimit = limit;
            this.piercing = limit > 1;
            return this;
        }
        public Builder inChest(boolean inChest) { this.inChest = inChest; return this; }
        public Builder particle(ParticleEffect particle) { this.particle = particle; return this; }

        public WeaponData build() {
            return new WeaponData(name, item, damage, bulletsPerShot, spread, delayTicks, reloadTicks, clipSize, maxReserve, goldPerHit, melee, piercing, pierceLimit, inChest, particle);
        }
    }
}
