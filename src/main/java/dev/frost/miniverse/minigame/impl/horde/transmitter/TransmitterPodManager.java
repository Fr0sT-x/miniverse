package dev.frost.miniverse.minigame.impl.horde.transmitter;

import java.util.Random;

import dev.frost.miniverse.minigame.core.shop.ShopGui;
import dev.frost.miniverse.minigame.impl.horde.HordeSurvivalMinigame;
import dev.frost.miniverse.minigame.impl.horde.shop.HordeShopProvider;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public class TransmitterPodManager {
    private final HordeSurvivalMinigame minigame;
    private final Random random = new Random();

    private TransmitterPod currentPod;
    private WanderingTraderEntity merchantEntity;

    public TransmitterPodManager(HordeSurvivalMinigame minigame) {
        this.minigame = minigame;
    }

    public TransmitterPod spawnPodForWave(ServerWorld world, BlockPos playerCenter, int wave, int uplinkDurationSeconds, float initialFuel, double harvestRadius, float fuelDrainPerSecond) {
        cleanupCurrentPod(world);

        BlockPos targetPos = findBestFlatSurface(world, playerCenter, 90, 140);
        this.currentPod = new TransmitterPod(targetPos, uplinkDurationSeconds, initialFuel, harvestRadius, fuelDrainPerSecond);
        this.currentPod.setup(world);

        // Visual and audio arrival explosion
        world.playSound(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.WEATHER, 3.0f, 0.8f);
        world.playSound(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 2.0f, 1.0f);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, targetPos.getX() + 0.5, targetPos.getY() + 1, targetPos.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticles(ParticleTypes.FLAME, targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, 40, 1.5, 0.5, 1.5, 0.05);

        // Distance & direction calculation
        int dx = targetPos.getX() - playerCenter.getX();
        int dz = targetPos.getZ() - playerCenter.getZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        String direction = getCardinalDirection(dx, dz);

        for (ServerPlayerEntity p : world.getPlayers()) {
            p.sendMessage(Text.literal("☄ MILITARY SUPPLY POD DETECTED! " + dist + "m " + direction + " [X: " + targetPos.getX() + ", Z: " + targetPos.getZ() + "] — Follow the smoke and boot the Uplink!").formatted(Formatting.GOLD, Formatting.BOLD), false);
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("☄ SUPPLY POD DETECTED!").formatted(Formatting.GOLD, Formatting.BOLD)));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(dist + "m " + direction + " [X: " + targetPos.getX() + ", Z: " + targetPos.getZ() + "]").formatted(Formatting.YELLOW)));
        }

        return this.currentPod;
    }

    public void tick(ServerWorld world) {
        if (world == null) return;

        if (this.currentPod != null) {
            this.currentPod.syncViewers(world.getPlayers());
            this.currentPod.tick(world);
        }
    }

    public boolean handleBlockInteraction(ServerPlayerEntity player, BlockPos clickedPos) {
        if (this.currentPod == null || clickedPos == null) return false;

        if (clickedPos.equals(this.currentPod.getPos()) || clickedPos.equals(this.currentPod.getPos().up())) {
            if (this.currentPod.getState() == TransmitterPod.State.AWAITING_ACTIVATION) {
                if (this.currentPod.activate(player.getServerWorld(), player)) {
                    this.minigame.broadcast(Text.literal("📡 " + player.getName().getString() + " BOOTED UP THE DATA UPLINK!").formatted(Formatting.GREEN, Formatting.BOLD));
                    this.minigame.broadcast(Text.literal("Hold the 20m perimeter! Kill mobs in the ring to keep the transmitter fueled!").formatted(Formatting.YELLOW));
                    return true;
                }
            }
        }
        return false;
    }

    public void spawnNomadMerchant(ServerWorld world, BlockPos pos) {
        if (this.merchantEntity != null) {
            this.merchantEntity.discard();
            this.merchantEntity = null;
        }

        BlockPos merchantPos = pos.east();
        if (!world.getBlockState(merchantPos).isAir()) {
            merchantPos = pos.north();
        }

        WanderingTraderEntity trader = new WanderingTraderEntity(EntityType.WANDERING_TRADER, world);
        trader.refreshPositionAndAngles(merchantPos.getX() + 0.5, merchantPos.getY(), merchantPos.getZ() + 0.5, 0.0f, 0.0f);
        trader.setCustomName(Text.literal("Nomad Merchant").formatted(Formatting.GOLD, Formatting.BOLD));
        trader.setCustomNameVisible(true);
        trader.setInvulnerable(true);
        trader.setAiDisabled(true);
        trader.setSilent(true);
        world.spawnEntity(trader);
        this.merchantEntity = trader;
    }

    public boolean handleEntityInteraction(ServerPlayerEntity player, Object target) {
        if (this.merchantEntity != null && target == this.merchantEntity) {
            ShopGui.open(player, Text.literal("Nomad Shop").formatted(Formatting.GOLD), HordeShopProvider.createCategories(this.minigame, false));
            return true;
        }
        return false;
    }

    public void cleanupCurrentPod(ServerWorld world) {
        if (this.currentPod != null) {
            this.currentPod.cleanup(world);
            this.currentPod = null;
        }
    }

    public void cleanup(ServerWorld world) {
        cleanupCurrentPod(world);
        if (this.merchantEntity != null) {
            this.merchantEntity.discard();
            this.merchantEntity = null;
        }
    }

    public TransmitterPod getCurrentPod() {
        return currentPod;
    }

    public WanderingTraderEntity getMerchantEntity() {
        return merchantEntity;
    }

    public static BlockPos findBestFlatSurface(ServerWorld world, BlockPos center, int minDistance, int maxDistance) {
        Random rng = new Random();
        BlockPos bestCandidate = null;
        int lowestScore = Integer.MAX_VALUE;

        // Test 64 candidate locations across 4 concentric radial rings to thoroughly scan terrain
        int rings = 4;
        int samplesPerRing = 16;
        for (int r = 0; r < rings; r++) {
            double ringDist = minDistance + ((double) r / (rings - 1)) * (maxDistance - minDistance);
            for (int s = 0; s < samplesPerRing; s++) {
                double angle = (2 * Math.PI * s) / samplesPerRing + (rng.nextDouble() * 0.15);
                int cx = (int) (center.getX() + ringDist * Math.cos(angle));
                int cz = (int) (center.getZ() + ringDist * Math.sin(angle));

                int baseY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                if (baseY <= world.getBottomY() + 5) continue;

                BlockState surfaceBlock = world.getBlockState(new BlockPos(cx, baseY - 1, cz));
                if (!surfaceBlock.getFluidState().isEmpty() || surfaceBlock.isAir()) continue;

                // 1. Check 7x7 footprint (dx: -3..3, dz: -3..3)
                int minY = baseY;
                int maxY = baseY;
                boolean invalid = false;

                for (int dx = -3; dx <= 3 && !invalid; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        int colY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz);
                        BlockState colBlock = world.getBlockState(new BlockPos(cx + dx, colY - 1, cz + dz));
                        if (!colBlock.getFluidState().isEmpty()) {
                            invalid = true;
                            break;
                        }
                        if (colY < minY) minY = colY;
                        if (colY > maxY) maxY = colY;
                    }
                }

                if (invalid) continue;

                int footprintVariance = maxY - minY;

                // 2. Anti-slope test: check 8 blocks out in all 4 cardinal directions to reject hillsides
                int northY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz - 8);
                int southY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz + 8);
                int eastY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx + 8, cz);
                int westY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx - 8, cz);
                int slopePenalty = Math.abs(northY - baseY) + Math.abs(southY - baseY) + Math.abs(eastY - baseY) + Math.abs(westY - baseY);

                // Score: lower is flatter, with heavy penalty on footprint variance and hill slopes
                int score = (footprintVariance * 12) + slopePenalty;

                if (score < lowestScore) {
                    lowestScore = score;
                    bestCandidate = new BlockPos(cx, baseY, cz);
                    // A pristine 7x7 flat plateau with zero slope
                    if (score <= 1) {
                        return bestCandidate;
                    }
                }
            }
        }

        if (bestCandidate != null) {
            return bestCandidate;
        }

        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, center.getX() + minDistance, center.getZ() + minDistance);
        return new BlockPos(center.getX() + minDistance, Math.max(world.getBottomY() + 5, y), center.getZ() + minDistance);
    }

    public static String getCardinalDirection(int dx, int dz) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360;
        if (angle >= 337.5 || angle < 22.5) return "South";
        if (angle >= 22.5 && angle < 67.5) return "South-West";
        if (angle >= 67.5 && angle < 112.5) return "West";
        if (angle >= 112.5 && angle < 157.5) return "North-West";
        if (angle >= 157.5 && angle < 202.5) return "North";
        if (angle >= 202.5 && angle < 247.5) return "North-East";
        if (angle >= 247.5 && angle < 292.5) return "East";
        return "South-East";
    }

    public static String getCardinalAbbreviation(int dx, int dz) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360;
        if (angle >= 337.5 || angle < 22.5) return "S";
        if (angle >= 22.5 && angle < 67.5) return "SW";
        if (angle >= 67.5 && angle < 112.5) return "W";
        if (angle >= 112.5 && angle < 157.5) return "NW";
        if (angle >= 157.5 && angle < 202.5) return "N";
        if (angle >= 202.5 && angle < 247.5) return "NE";
        if (angle >= 247.5 && angle < 292.5) return "E";
        return "SE";
    }
}
