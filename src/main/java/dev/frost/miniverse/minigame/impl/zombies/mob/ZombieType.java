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
        .equip(EquipmentSlot.HEAD, createGuardianHead())),

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

    public boolean isBoss() {
        return this == BOMBIE || this == INFERNO || this == BROODMOTHER;
    }

    public static final String GUARDIAN_HEAD_UUID_STRING = "603da958-8b96-4131-b3b3-8e4014f3b602";
    public static final java.util.UUID GUARDIAN_HEAD_UUID = java.util.UUID.fromString(GUARDIAN_HEAD_UUID_STRING);

    public static ItemStack createGuardianHead() {
        java.util.UUID guardianUuid = java.util.UUID.fromString(GUARDIAN_HEAD_UUID_STRING);
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        com.mojang.authlib.properties.PropertyMap properties = new com.mojang.authlib.properties.PropertyMap();
        properties.put("textures", new com.mojang.authlib.properties.Property(
            "textures",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGZiNjc1Y2I1YTc2ZDAzNGRlMmU0NDIzNDNhNmUwZjlhMmUyYzQ2MGJkNzg5NTlhZGI2MWY0ODFhNDk4NTE5OCJ9fX0="
        ));
        net.minecraft.component.type.ProfileComponent profile = new net.minecraft.component.type.ProfileComponent(
            java.util.Optional.of("Guardian"),
            java.util.Optional.of(guardianUuid),
            properties
        );
        head.set(net.minecraft.component.DataComponentTypes.PROFILE, profile);
        net.minecraft.component.type.NbtComponent.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, head, nbt -> {
            nbt.putBoolean("miniverse_guardian_head", true);
        });
        return head;
    }

    public static boolean isGuardianHead(ItemStack stack) {
        if (stack == null || !stack.isOf(Items.PLAYER_HEAD)) {
            return false;
        }
        net.minecraft.component.type.NbtComponent comp = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (comp != null && comp.contains("miniverse_guardian_head")) {
            return true;
        }
        net.minecraft.component.type.ProfileComponent profile = stack.get(net.minecraft.component.DataComponentTypes.PROFILE);
        return profile != null && profile.id().isPresent() && GUARDIAN_HEAD_UUID_STRING.equalsIgnoreCase(profile.id().get().toString());
    }
}
