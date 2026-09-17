package dev.frost.miniverse.minigame.impl.zombies.mob;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public enum ZombieType {
    NORMAL_EASY(new ZombieMobData()
        .entityType(EntityType.ZOMBIE)
        .health(10.0f)
        .speed(0.22)
        .breakWindowTicks(40)),

    NORMAL_MEDIUM(new ZombieMobData()
        .entityType(EntityType.ZOMBIE)
        .health(12.0f)
        .speed(0.24)
        .breakWindowTicks(30)
        .equip(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE))
        .equip(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS))),

    NORMAL_HARD(new ZombieMobData()
        .entityType(EntityType.ZOMBIE)
        .health(15.0f)
        .speed(0.25)
        .breakWindowTicks(20)
        .equip(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE))
        .equip(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS))
        .equip(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE))),

    PIG_ZOMBIE(new ZombieMobData()
        .entityType(EntityType.ZOMBIFIED_PIGLIN)
        .health(10.0f)
        .speed(0.28)
        .fireImmune(true)
        .breakWindowTicks(20)
        .equip(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD))),

    MAGMA_CUBE(new ZombieMobData()
        .entityType(EntityType.MAGMA_CUBE)
        .health(4.0f)
        .speed(0.25)
        .fireImmune(true)
        .breakWindowTicks(20)),

    MAGMA_ZOMBIE(new ZombieMobData()
        .entityType(EntityType.ZOMBIE)
        .health(8.0f)
        .speed(0.25)
        .fireImmune(true)
        .breakWindowTicks(20)
        .equip(EquipmentSlot.HEAD, new ItemStack(Items.MAGMA_BLOCK))
        .equip(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE))
        .equip(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS))
        .equip(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS))
        .equip(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD))),

    LITTLE_BOMBIE(new ZombieMobData()
        .entityType(EntityType.ZOMBIE)
        .health(10.0f)
        .speed(0.32)
        .baby(true)
        .breakWindowTicks(20)
        .equip(EquipmentSlot.HEAD, new ItemStack(Items.TNT))),

    FIRE_ZOMBIE(new ZombieMobData()
        .entityType(EntityType.ZOMBIE)
        .health(12.0f)
        .speed(0.25)
        .fireImmune(true)
        .breakWindowTicks(20)
        .equip(EquipmentSlot.MAINHAND, new ItemStack(Items.BLAZE_ROD))),

    ZOMBIE_WOLF(new ZombieMobData()
        .entityType(EntityType.WOLF)
        .health(10.0f)
        .speed(0.34)
        .breakWindowTicks(20)),

    GUARDIAN_ZOMBIE(new ZombieMobData()
        .entityType(EntityType.ZOMBIE)
        .health(14.0f)
        .speed(0.23)
        .breakWindowTicks(30)
        .equip(EquipmentSlot.HEAD, new ItemStack(Items.SEA_LANTERN))),

    BOMBIE(new ZombieMobData()
        .entityType(EntityType.ZOMBIE)
        .health(100.0f)
        .speed(0.24)
        .breakWindowTicks(15)
        .equip(EquipmentSlot.HEAD, new ItemStack(Items.TNT))
        .equip(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE))),

    INFERNO(new ZombieMobData()
        .entityType(EntityType.ZOMBIE)
        .health(120.0f)
        .speed(0.25)
        .fireImmune(true)
        .breakWindowTicks(15)
        .equip(EquipmentSlot.HEAD, new ItemStack(Items.NETHERRACK))
        .equip(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE))
        .equip(EquipmentSlot.MAINHAND, new ItemStack(Items.BLAZE_ROD))),

    BROODMOTHER(new ZombieMobData()
        .entityType(EntityType.CAVE_SPIDER)
        .health(250.0f)
        .speed(0.35)
        .breakWindowTicks(10));

    private final ZombieMobData data;

    ZombieType(ZombieMobData data) {
        this.data = data;
    }

    public ZombieMobData getData() {
        return this.data;
    }
}
