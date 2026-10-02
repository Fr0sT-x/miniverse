package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.editor.RegionPart;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyArenaHelper;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMapConfig;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HighGroundRule implements MicroRule {
    private final Random random = new Random();
    private double floorBaselineY = 100.0;
    private final List<RegionPart> activeHighGroundRegions = new ArrayList<>();

    private static BlockState[] getHighGroundBlocks() {
        return new BlockState[] {
            Blocks.STONE_BRICKS.getDefaultState(),
            Blocks.COBBLESTONE.getDefaultState(),
            Blocks.CHISELED_STONE_BRICKS.getDefaultState(),
            Blocks.GOLD_BLOCK.getDefaultState()
        };
    }

    @Override
    public String id() {
        return "high_ground";
    }

    @Override
    public String name() {
        return "High Ground";
    }

    @Override
    public String description() {
        return "The floor is lava! Jump onto elevated platforms.";
    }

    @Override
    public Text title() {
        return Text.literal("THE FLOOR IS LAVA!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Get onto high ground immediately!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public int getDurationTicks(MicroFrenzyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(8 * 20 * factor);
        return Math.max(80, standardTicks); // Clamped to at least 4.0 seconds (80 ticks)
    }

    @Override
    public boolean isApplicable(MicroFrenzyMapConfig mapConfig) {
        return true;
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        this.activeHighGroundRegions.clear();
        this.floorBaselineY = MicroFrenzyArenaHelper.getFloorY(game.getMapConfig());

        List<RegionPart> staticHigh = game.getMapConfig().highGround();
        if (!staticHigh.isEmpty()) {
            this.activeHighGroundRegions.addAll(staticHigh);
            return;
        }

        // Procedurally generate 1-block elevated jumpable high-ground platforms
        ServerWorld world = game.getWorld();
        if (world == null) return;

        int floorY = (int) Math.floor(this.floorBaselineY);
        int surfaceY = floorY - 1;
        MicroFrenzyArenaHelper.ArenaBounds2D bounds = MicroFrenzyArenaHelper.getBounds2D(game.getMapConfig());

        int platformCount = 4 + random.nextInt(4); // 4 to 7 platforms

        BlockState[] highGroundBlocks = getHighGroundBlocks();
        for (int p = 0; p < platformCount; p++) {
            BlockState block = highGroundBlocks[random.nextInt(highGroundBlocks.length)];

            // Pick a random location within bounds
            int margin = Math.max(2, bounds.width() / 6);
            int cx = bounds.minX() + margin + random.nextInt(Math.max(1, bounds.width() - 2 * margin));
            int cz = bounds.minZ() + margin + random.nextInt(Math.max(1, bounds.depth() - 2 * margin));

            // Varied platform sizes: 1x1 pillar, 2x2 platform, or 3x3 platform
            int sizeRoll = random.nextInt(10);
            int rad = sizeRoll < 4 ? 0 : (sizeRoll < 8 ? 1 : 1); // 0 = 1x1, 1 = 2x2 or 3x3
            int minX = cx - rad;
            int maxX = cx + (rad == 1 && random.nextBoolean() ? 0 : rad);
            int minZ = cz - rad;
            int maxZ = cz + (rad == 1 && random.nextBoolean() ? 0 : rad);

            boolean placedAny = false;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos surfacePos = new BlockPos(x, surfaceY, z);
                    BlockPos highGroundPos = new BlockPos(x, floorY, z);
                    // Only place high ground on top of solid floor blocks with air above
                    if (!world.getBlockState(surfacePos).isAir() && world.getBlockState(highGroundPos).isAir()) {
                        game.getBlockManager().setTemporaryBlock(world, highGroundPos, block);
                        placedAny = true;
                    }
                }
            }

            if (placedAny) {
                RegionPart region = new RegionPart(
                    new MapPosition(minX, floorY, minZ, 0, 0),
                    new MapPosition(maxX, floorY + 2, maxZ, 0, 0)
                );
                this.activeHighGroundRegions.add(region);
            }
        }
    }

    @Override
    public void onTick(MicroFrenzyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        MicroFrenzyArenaHelper.ArenaBounds2D bounds = MicroFrenzyArenaHelper.getBounds2D(game.getMapConfig());
        double y = this.floorBaselineY + 0.1;

        // Lava particles across the arena floor
        for (int i = 0; i < 6; i++) {
            double rx = bounds.minX() + random.nextDouble() * bounds.width();
            double rz = bounds.minZ() + random.nextDouble() * bounds.depth();
            world.spawnParticles(ParticleTypes.LAVA, rx, y, rz, 1, 0.1, 0.05, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.FLAME, rx, y, rz, 1, 0.2, 0.05, 0.2, 0.01);
        }

        if (remainingTicks % 15 == 0 && game.getMapConfig().arenaCenter() != null) {
            MapPosition c = game.getMapConfig().arenaCenter();
            world.playSound(null, c.x(), y, c.z(), SoundEvents.BLOCK_LAVA_AMBIENT, SoundCategory.BLOCKS, 0.7f, 1.2f);
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        // 1. Check custom or procedurally generated high ground regions
        if (!this.activeHighGroundRegions.isEmpty()) {
            Vec3d pos = player.getPos();
            for (RegionPart r : this.activeHighGroundRegions) {
                if (regionContains(r, pos)) {
                    return true;
                }
            }
        }
        // 2. Fallback: check vertical height above floor baseline (standing on 1-block high ground)
        return player.getY() >= this.floorBaselineY + 0.8;
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        this.activeHighGroundRegions.clear();
        if (game.getWorld() != null) {
            game.getBlockManager().restoreAll(game.getWorld());
        }
    }

    private static boolean regionContains(RegionPart r, Vec3d pos) {
        double minX = Math.min(r.min().x(), r.max().x());
        double maxX = Math.max(r.min().x(), r.max().x()) + 1.0;
        double minY = Math.min(r.min().y(), r.max().y()) - 0.5;
        double maxY = Math.max(r.min().y(), r.max().y()) + 2.5;
        double minZ = Math.min(r.min().z(), r.max().z());
        double maxZ = Math.max(r.min().z(), r.max().z()) + 1.0;
        return pos.x >= minX && pos.x <= maxX &&
               pos.y >= minY && pos.y <= maxY &&
               pos.z >= minZ && pos.z <= maxZ;
    }
}
