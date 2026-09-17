package dev.frost.miniverse.minigame.impl.zombies.station;

import dev.frost.miniverse.map.editor.RegionPart;
import dev.frost.miniverse.minigame.impl.zombies.map.*;
import dev.frost.miniverse.mixin.DisplayEntityAccessor;
import dev.frost.miniverse.mixin.TextDisplayEntityAccessor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class ZombiesHologramManager {
    private final ServerWorld world;
    private final Map<String, DisplayEntity.TextDisplayEntity> doorHolograms = new HashMap<>();
    private final List<DisplayEntity.TextDisplayEntity> staticHolograms = new ArrayList<>();
    private DisplayEntity.TextDisplayEntity powerSwitchHologram = null;
    private DisplayEntity.TextDisplayEntity luckyChestHologram = null;

    public ZombiesHologramManager(ServerWorld world) {
        this.world = world;
    }

    public static Vec3d getChestCenter(ServerWorld world, BlockPos pos) {
        if (world != null && pos != null) {
            net.minecraft.block.BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.block.ChestBlock) {
                net.minecraft.block.enums.ChestType type = state.get(net.minecraft.block.ChestBlock.CHEST_TYPE);
                if (type != net.minecraft.block.enums.ChestType.SINGLE) {
                    for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                        BlockPos adj = pos.offset(dir);
                        net.minecraft.block.BlockState adjState = world.getBlockState(adj);
                        if (adjState.getBlock() instanceof net.minecraft.block.ChestBlock && adjState.get(net.minecraft.block.ChestBlock.FACING) == state.get(net.minecraft.block.ChestBlock.FACING)) {
                            return new Vec3d(
                                (pos.getX() + adj.getX() + 1.0) / 2.0,
                                pos.getY() + 0.85,
                                (pos.getZ() + adj.getZ() + 1.0) / 2.0
                            );
                        }
                    }
                }
            }
        }
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5);
    }

    public DisplayEntity.TextDisplayEntity createHologram(double x, double y, double z, Text text) {
        DisplayEntity.TextDisplayEntity display = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, this.world);
        display.setPosition(x, y, z);
        
        TextDisplayEntityAccessor textAccessor = (TextDisplayEntityAccessor) display;
        DisplayEntityAccessor displayAccessor = (DisplayEntityAccessor) display;

        textAccessor.callSetText(text);
        displayAccessor.callSetBillboardMode(DisplayEntity.BillboardMode.CENTER);
        displayAccessor.callSetViewRange(0.6f); // ~38 blocks view range: visible across rooms, occluded by walls

        // Depth occlusion: Ensure SEE_THROUGH_FLAG is NOT set (so walls occlude text)
        byte flags = textAccessor.callGetDisplayFlags();
        flags = (byte) (flags & ~DisplayEntity.TextDisplayEntity.SEE_THROUGH_FLAG);
        flags = (byte) (flags | DisplayEntity.TextDisplayEntity.SHADOW_FLAG);
        textAccessor.callSetDisplayFlags(flags);

        this.world.spawnEntity(display);
        return display;
    }

    public void spawnAll(ZombiesMapConfig mapConfig, BlockPos ultimateMachinePos) {
        if (mapConfig == null) return;

        // 1. Doors
        for (ZombiesDoor door : mapConfig.doors()) {
            RegionPart b = door.getBounds();
            if (b != null) {
                double minX = Math.min(b.min().x(), b.max().x());
                double maxX = Math.max(b.min().x(), b.max().x());
                double minY = Math.min(b.min().y(), b.max().y());
                double minZ = Math.min(b.min().z(), b.max().z());
                double maxZ = Math.max(b.min().z(), b.max().z());

                double cx = (minX + maxX) / 2.0 + 0.5;
                double cy = minY + 1.2;
                double cz = (minZ + maxZ) / 2.0 + 0.5;

                Text doorText = Text.empty()
                    .append(Text.literal("Open " + door.getArea2()).formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal("\n" + door.getGold() + " Gold").formatted(Formatting.YELLOW));

                DisplayEntity.TextDisplayEntity holo = createHologram(cx, cy, cz, doorText);
                this.doorHolograms.put(door.getId(), holo);
            }
        }

        // 2. Weapon Shops
        for (ZombiesWeaponShop shop : mapConfig.weaponShops()) {
            BlockPos p = shop.getPos();
            if (p != null) {
                Text weaponText = Text.empty()
                    .append(Text.literal(shop.getWeaponType().getData().displayName()).formatted(Formatting.AQUA, Formatting.BOLD))
                    .append(Text.literal("\nCost: " + shop.getPurchasePrice() + "g | Refill: " + shop.getRefillPrice() + "g").formatted(Formatting.YELLOW));

                DisplayEntity.TextDisplayEntity holo = createHologram(p.getX() + 0.5, p.getY() + 0.75, p.getZ() + 0.5, weaponText);
                this.staticHolograms.add(holo);
            }
        }

        // 3. Armor Shops
        for (ZombiesArmorShop shop : mapConfig.armorShops()) {
            BlockPos p = shop.getPos();
            if (p != null) {
                String part = shop.getPart() == ZombiesArmorShop.ArmorPart.UPPER_BODY ? "Chest & Helmet" : "Legs & Boots";
                Text armorText = Text.empty()
                    .append(Text.literal(shop.getQuality().name() + " Armor (" + part + ")").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
                    .append(Text.literal("\nCost: " + shop.getPrice() + "g").formatted(Formatting.YELLOW));

                DisplayEntity.TextDisplayEntity holo = createHologram(p.getX() + 0.5, p.getY() + 0.75, p.getZ() + 0.5, armorText);
                this.staticHolograms.add(holo);
            }
        }

        // 4. Perk Machines
        for (ZombiesPerkMachine machine : mapConfig.perkMachines()) {
            BlockPos p = machine.getPos();
            if (p != null) {
                Text perkText = Text.empty()
                    .append(machine.getPerk().toFormattedText())
                    .append(Text.literal("\nCost: " + machine.getGold() + "g").formatted(Formatting.YELLOW));

                DisplayEntity.TextDisplayEntity holo = createHologram(p.getX() + 0.5, p.getY() + 1.25, p.getZ() + 0.5, perkText);
                this.staticHolograms.add(holo);
            }
        }

        // 5. Power Switch
        if (mapConfig.powerSwitch() != null && mapConfig.powerSwitch().getPos() != null) {
            BlockPos p = mapConfig.powerSwitch().getPos();
            int cost = mapConfig.powerSwitch().getGold() > 0 ? mapConfig.powerSwitch().getGold() : 1000;
            Text powerText = Text.empty()
                .append(Text.literal("⚡ Power Switch").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("\nCost: " + cost + "g (Click to restore power)").formatted(Formatting.YELLOW));

            DisplayEntity.TextDisplayEntity holo = createHologram(p.getX() + 0.5, p.getY() + 0.75, p.getZ() + 0.5, powerText);
            this.staticHolograms.add(holo);
            this.powerSwitchHologram = holo;
        }

        // 6. Team Machine
        if (mapConfig.teamMachine() != null && mapConfig.teamMachine().getPos() != null) {
            BlockPos p = mapConfig.teamMachine().getPos();
            Text teamText = Text.empty()
                .append(Text.literal("Team Machine").formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal("\nDowned & Team Revives").formatted(Formatting.YELLOW));

            DisplayEntity.TextDisplayEntity holo = createHologram(p.getX() + 0.5, p.getY() + 1.25, p.getZ() + 0.5, teamText);
            this.staticHolograms.add(holo);
        }

        // 7. Ultimate Machine
        if (ultimateMachinePos != null) {
            Text ultText = Text.empty()
                .append(Text.literal("★ Ultimate Machine ★").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
                .append(Text.literal("\nCost: 1000g (Weapon Upgrade)").formatted(Formatting.YELLOW));

            DisplayEntity.TextDisplayEntity holo = createHologram(ultimateMachinePos.getX() + 0.5, ultimateMachinePos.getY() + 1.25, ultimateMachinePos.getZ() + 0.5, ultText);
            this.staticHolograms.add(holo);
        }

        // 8. Lucky Chests (Mystery Boxes)
        for (ZombiesLuckyChest lc : mapConfig.luckyChests()) {
            BlockPos p = lc.getPos();
            if (p != null) {
                Vec3d center = getChestCenter(this.world, p);
                Text chestText = Text.empty()
                    .append(Text.literal("★ Mystery Box ★").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
                    .append(Text.literal("\n" + lc.getGold() + " Gold").formatted(Formatting.YELLOW))
                    .append(Text.literal("\n(Right-Click to Roll)").formatted(Formatting.GRAY));

                DisplayEntity.TextDisplayEntity holo = createHologram(center.x, center.y, center.z, chestText);
                this.staticHolograms.add(holo);
            }
        }
    }

    public void updateLuckyChestHologram(BlockPos pos) {
        if (this.luckyChestHologram != null && !this.luckyChestHologram.isRemoved()) {
            this.luckyChestHologram.discard();
            this.luckyChestHologram = null;
        }

        if (pos != null) {
            Vec3d center = getChestCenter(this.world, pos);
            Text chestText = Text.empty()
                .append(Text.literal("★ Mystery Box ★").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
                .append(Text.literal("\n1000 Gold").formatted(Formatting.YELLOW))
                .append(Text.literal("\n(Right-Click to Roll)").formatted(Formatting.GRAY));

            this.luckyChestHologram = createHologram(center.x, center.y, center.z, chestText);
        }
    }

    public void removeDoorHologram(String doorId) {
        if (doorId == null) return;
        DisplayEntity.TextDisplayEntity holo = this.doorHolograms.remove(doorId);
        if (holo != null && !holo.isRemoved()) {
            holo.discard();
        }
    }

    public void onPowerActivated() {
        if (this.powerSwitchHologram != null && !this.powerSwitchHologram.isRemoved()) {
            Text activeText = Text.empty()
                .append(Text.literal("⚡ Power Switch").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("\n✔ Power Active").formatted(Formatting.GREEN, Formatting.BOLD));
            ((TextDisplayEntityAccessor) this.powerSwitchHologram).callSetText(activeText);
        }
    }

    public void clear() {
        for (DisplayEntity.TextDisplayEntity holo : this.doorHolograms.values()) {
            if (holo != null && !holo.isRemoved()) holo.discard();
        }
        this.doorHolograms.clear();

        for (DisplayEntity.TextDisplayEntity holo : this.staticHolograms) {
            if (holo != null && !holo.isRemoved()) holo.discard();
        }
        this.staticHolograms.clear();

        if (this.luckyChestHologram != null && !this.luckyChestHologram.isRemoved()) {
            this.luckyChestHologram.discard();
            this.luckyChestHologram = null;
        }
        this.powerSwitchHologram = null;
    }
}
