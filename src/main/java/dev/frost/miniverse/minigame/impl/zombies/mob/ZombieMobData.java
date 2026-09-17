package dev.frost.miniverse.minigame.impl.zombies.mob;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ZombieMobData {
    private EntityType<?> entityType = EntityType.ZOMBIE;
    private float health = 10.0f;
    private double speed = 0.23;
    private boolean fireImmune = false;
    private boolean baby = false;
    private int breakWindowTicks = 30;
    private final Map<EquipmentSlot, ItemStack> equipment = new HashMap<>();

    public EntityType<?> getEntityType() { return entityType; }
    public float getHealth() { return health; }
    public double getSpeed() { return speed; }
    public boolean isFireImmune() { return fireImmune; }
    public boolean isBaby() { return baby; }
    public int getBreakWindowTicks() { return breakWindowTicks; }
    public Map<EquipmentSlot, ItemStack> getEquipment() { return equipment; }

    public ZombieMobData entityType(EntityType<?> type) { this.entityType = type; return this; }
    public ZombieMobData health(float health) { this.health = health; return this; }
    public ZombieMobData speed(double speed) { this.speed = speed; return this; }
    public ZombieMobData fireImmune(boolean fireImmune) { this.fireImmune = fireImmune; return this; }
    public ZombieMobData baby(boolean baby) { this.baby = baby; return this; }
    public ZombieMobData breakWindowTicks(int ticks) { this.breakWindowTicks = ticks; return this; }
    public ZombieMobData equip(EquipmentSlot slot, ItemStack item) { this.equipment.put(slot, item); return this; }
}
