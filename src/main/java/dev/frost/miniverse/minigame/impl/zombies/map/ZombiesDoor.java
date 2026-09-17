package dev.frost.miniverse.minigame.impl.zombies.map;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.editor.RegionPart;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

public class ZombiesDoor {
    private final String id;
    private final String area1;
    private final String area2;
    private final int gold;
    private final RegionPart bounds;
    private final RegionPart templateClosed;
    private boolean open;

    public ZombiesDoor(String id, String area1, String area2, int gold, RegionPart bounds, RegionPart templateClosed) {
        this.id = id;
        this.area1 = area1;
        this.area2 = area2;
        this.gold = gold;
        this.bounds = bounds;
        this.templateClosed = templateClosed;
        this.open = false;
    }

    public String getId() {
        return this.id;
    }

    public String getArea1() {
        return this.area1;
    }

    public String getArea2() {
        return this.area2;
    }

    public int getGold() {
        return this.gold;
    }

    public RegionPart getBounds() {
        return this.bounds;
    }

    public RegionPart getTemplateClosed() {
        return this.templateClosed;
    }

    public boolean isOpen() {
        return this.open;
    }

    public boolean contains(BlockPos pos) {
        if (this.bounds == null) return false;
        double minX = Math.min(this.bounds.min().x(), this.bounds.max().x());
        double maxX = Math.max(this.bounds.min().x(), this.bounds.max().x());
        double minY = Math.min(this.bounds.min().y(), this.bounds.max().y());
        double maxY = Math.max(this.bounds.min().y(), this.bounds.max().y());
        double minZ = Math.min(this.bounds.min().z(), this.bounds.max().z());
        double maxZ = Math.max(this.bounds.min().z(), this.bounds.max().z());
        return pos.getX() >= minX && pos.getX() <= maxX &&
               pos.getY() >= minY && pos.getY() <= maxY &&
               pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public boolean isNearDoor(double x, double y, double z, double maxDistance) {
        if (this.bounds == null) return false;
        double minX = Math.min(this.bounds.min().x(), this.bounds.max().x());
        double maxX = Math.max(this.bounds.min().x(), this.bounds.max().x()) + 1.0;
        double minY = Math.min(this.bounds.min().y(), this.bounds.max().y());
        double maxY = Math.max(this.bounds.min().y(), this.bounds.max().y()) + 1.0;
        double minZ = Math.min(this.bounds.min().z(), this.bounds.max().z());
        double maxZ = Math.max(this.bounds.min().z(), this.bounds.max().z()) + 1.0;

        double dx = Math.max(0.0, Math.max(minX - x, x - maxX));
        double dy = Math.max(0.0, Math.max(minY - y, y - maxY));
        double dz = Math.max(0.0, Math.max(minZ - z, z - maxZ));

        return (dx * dx + dy * dy + dz * dz) <= (maxDistance * maxDistance);
    }

    public void openDoor(ServerWorld world) {
        if (this.open) return;
        this.open = true;

        if (this.bounds != null) {
            int minX = (int) Math.min(this.bounds.min().x(), this.bounds.max().x());
            int maxX = (int) Math.max(this.bounds.min().x(), this.bounds.max().x());
            int minY = (int) Math.min(this.bounds.min().y(), this.bounds.max().y());
            int maxY = (int) Math.max(this.bounds.min().y(), this.bounds.max().y());
            int minZ = (int) Math.min(this.bounds.min().z(), this.bounds.max().z());
            int maxZ = (int) Math.max(this.bounds.min().z(), this.bounds.max().z());

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            world.playSound(null, (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.5f, 0.8f);
        }
    }

    public void closeDoor(ServerWorld world) {
        this.open = false;
        if (this.bounds == null) return;

        int minX = (int) Math.min(this.bounds.min().x(), this.bounds.max().x());
        int maxX = (int) Math.max(this.bounds.min().x(), this.bounds.max().x());
        int minY = (int) Math.min(this.bounds.min().y(), this.bounds.max().y());
        int maxY = (int) Math.max(this.bounds.min().y(), this.bounds.max().y());
        int minZ = (int) Math.min(this.bounds.min().z(), this.bounds.max().z());
        int maxZ = (int) Math.max(this.bounds.min().z(), this.bounds.max().z());

        if (this.templateClosed != null) {
            int tMinX = (int) Math.min(this.templateClosed.min().x(), this.templateClosed.max().x());
            int tMinY = (int) Math.min(this.templateClosed.min().y(), this.templateClosed.max().y());
            int tMinZ = (int) Math.min(this.templateClosed.min().z(), this.templateClosed.max().z());

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        int srcX = tMinX + (x - minX);
                        int srcY = tMinY + (y - minY);
                        int srcZ = tMinZ + (z - minZ);
                        BlockState state = world.getBlockState(new BlockPos(srcX, srcY, srcZ));
                        world.setBlockState(new BlockPos(x, y, z), state.isAir() ? Blocks.IRON_BARS.getDefaultState() : state, 3);
                    }
                }
            }
        } else {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.IRON_BARS.getDefaultState(), 3);
                    }
                }
            }
        }
    }
}
