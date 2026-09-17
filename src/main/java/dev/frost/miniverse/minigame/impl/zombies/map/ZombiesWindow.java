package dev.frost.miniverse.minigame.impl.zombies.map;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.editor.RegionPart;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ZombiesWindow {
    private final String id;
    private final String area;
    private final MapPosition spawnPos;
    private final RegionPart blockBounds;
    private final RegionPart repairZone;
    private final List<BlockPos> barricadePositions = new ArrayList<>();
    private long lastAttackedTime = 0;

    public ZombiesWindow(String id, String area, MapPosition spawnPos, RegionPart blockBounds, RegionPart repairZone) {
        this.id = id;
        this.area = area;
        this.spawnPos = spawnPos;
        this.blockBounds = blockBounds;
        this.repairZone = repairZone;

        if (this.blockBounds != null) {
            int minX = (int) Math.min(this.blockBounds.min().x(), this.blockBounds.max().x());
            int maxX = (int) Math.max(this.blockBounds.min().x(), this.blockBounds.max().x());
            int minY = (int) Math.min(this.blockBounds.min().y(), this.blockBounds.max().y());
            int maxY = (int) Math.max(this.blockBounds.min().y(), this.blockBounds.max().y());
            int minZ = (int) Math.min(this.blockBounds.min().z(), this.blockBounds.max().z());
            int maxZ = (int) Math.max(this.blockBounds.min().z(), this.blockBounds.max().z());

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        this.barricadePositions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    public String getId() {
        return this.id;
    }

    public String getArea() {
        return this.area;
    }

    public MapPosition getSpawnPos() {
        return this.spawnPos;
    }

    public RegionPart getBlockBounds() {
        return this.blockBounds;
    }

    public RegionPart getRepairZone() {
        return this.repairZone;
    }

    public List<BlockPos> getBarricadePositions() {
        return this.barricadePositions;
    }

    public boolean isInRepairZone(double x, double y, double z) {
        if (this.repairZone == null) return false;
        double minX = Math.min(this.repairZone.min().x(), this.repairZone.max().x());
        double maxX = Math.max(this.repairZone.min().x(), this.repairZone.max().x()) + 1.0;
        double minY = Math.min(this.repairZone.min().y(), this.repairZone.max().y());
        double maxY = Math.max(this.repairZone.min().y(), this.repairZone.max().y()) + 2.5;
        double minZ = Math.min(this.repairZone.min().z(), this.repairZone.max().z());
        double maxZ = Math.max(this.repairZone.min().z(), this.repairZone.max().z()) + 1.0;

        return x >= minX - 0.75 && x <= maxX + 0.75 &&
               y >= minY - 0.5 && y <= maxY + 0.5 &&
               z >= minZ - 0.75 && z <= maxZ + 0.75;
    }

    public boolean isNearWindow(double x, double y, double z, double maxDistanceSq) {
        if (isInRepairZone(x, y, z)) return true;
        for (BlockPos pos : this.barricadePositions) {
            double dx = x - (pos.getX() + 0.5);
            double dy = y - (pos.getY() + 0.5);
            double dz = z - (pos.getZ() + 0.5);
            if ((dx * dx + dy * dy + dz * dz) <= maxDistanceSq) {
                return true;
            }
        }
        return false;
    }

    public boolean isFullyRepaired(ServerWorld world) {
        for (BlockPos pos : this.barricadePositions) {
            if (world.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    public boolean isCompletelyBroken(ServerWorld world) {
        for (BlockPos pos : this.barricadePositions) {
            if (!world.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    public boolean hasIntactSlabs(ServerWorld world) {
        return !isCompletelyBroken(world);
    }

    public BlockPos getNearestIntactBarricade(BlockPos entityPos, ServerWorld world, double maxDistSq) {
        BlockPos nearest = null;
        double bestDist = maxDistSq;
        for (BlockPos pos : this.barricadePositions) {
            if (!world.getBlockState(pos).isAir()) {
                double dist = entityPos.getSquaredDistance(pos);
                if (dist <= bestDist) {
                    bestDist = dist;
                    nearest = pos;
                }
            }
        }
        return nearest;
    }

    public boolean repairOneSlab(ServerWorld world) {
        for (BlockPos pos : this.barricadePositions) {
            if (world.getBlockState(pos).isAir()) {
                world.setBlockState(pos, Blocks.OAK_SLAB.getDefaultState(), 3);
                world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.getDefaultState()), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
                return true;
            }
        }
        return false;
    }

    public boolean breakOneSlab(ServerWorld world) {
        markAttacked();
        for (int i = this.barricadePositions.size() - 1; i >= 0; i--) {
            BlockPos pos = this.barricadePositions.get(i);
            if (!world.getBlockState(pos).isAir()) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.0f, 0.9f);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.getDefaultState()), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.05);
                return true;
            }
        }
        return false;
    }

    public void playBarricadeHit(ServerWorld world, BlockPos pos) {
        markAttacked();
        world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 0.8f, 0.8f);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.getDefaultState()), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0.02);
    }

    public void markAttacked() {
        this.lastAttackedTime = System.currentTimeMillis();
    }

    public boolean isUnderAttack() {
        return (System.currentTimeMillis() - this.lastAttackedTime) < 2000L;
    }

    public void reset(ServerWorld world) {
        for (BlockPos pos : this.barricadePositions) {
            world.setBlockState(pos, Blocks.OAK_SLAB.getDefaultState(), 3);
        }
    }
}
