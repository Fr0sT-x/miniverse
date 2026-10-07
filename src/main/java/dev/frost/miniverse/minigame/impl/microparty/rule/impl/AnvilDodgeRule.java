package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnvilDodgeRule implements MicroRule {
    private final Random random = new Random();
    private final List<FallingBlockEntity> spawnedAnvils = new ArrayList<>();
    private final List<BlockPos> landedAnvilBlocks = new ArrayList<>();
    private boolean secondWaveSpawned = false;

    @Override
    public String id() {
        return "anvil_dodge";
    }

    @Override
    public String name() {
        return "Anvil Dodge";
    }

    @Override
    public String description() {
        return "Watch the ground shadows and dodge the falling anvils.";
    }

    @Override
    public Text title() {
        return Text.literal("DODGE THE ANVILS!").formatted(Formatting.GRAY, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Watch the shadows on the ground and dodge!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public double minDurationSeconds() {
        return 4.5;
    }

    private boolean firstWaveSpawned = false;
    private int ticksElapsed = 0;

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        spawnedAnvils.clear();
        landedAnvilBlocks.clear();
        firstWaveSpawned = false;
        secondWaveSpawned = false;
        ticksElapsed = 0;

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            game.getTracker().setPassedCurrentRound(p.getUuid(), true); // Default pass unless crushed
            p.sendMessage(Text.literal("§e⚠ Watch the sky! Anvils incoming in §c1.2s§e..."), true);
            p.playSoundToPlayer(SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.7f, 1.4f);
        }
    }

    private void spawnAnvilWave(MicroPartyMinigame game, ServerWorld world, int waveIndex) {
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        int spawnY = floorY + 12;

        // With step 3, in each 3x3 pocket we pick at most 1 anvil drop location.
        // Staggering by waveIndex ensures wave 2 drops in different grid corridors.
        int offset = (waveIndex % 2 == 0) ? 0 : 1;

        for (int x = bounds.minX() + 1 + offset; x <= bounds.maxX() - 1; x += 3) {
            for (int z = bounds.minZ() + 1 + offset; z <= bounds.maxZ() - 1; z += 3) {
                // Ensure platform beneath exists (skip void or holes in platform)
                BlockPos floorPos = new BlockPos(x, floorY - 1, z);
                if (world.getBlockState(floorPos).isAir()) {
                    continue;
                }

                // ~55% probability in each 3x3 cell, leaving ~45% clear pockets and 2-block gaps everywhere
                if (random.nextFloat() < 0.55f) {
                    BlockPos dropPos = new BlockPos(x, spawnY, z);
                    BlockPos groundPos = new BlockPos(x, floorY, z);

                    FallingBlockEntity anvil = FallingBlockEntity.spawnFromBlock(world, dropPos, Blocks.DAMAGED_ANVIL.getDefaultState());
                    anvil.setHurtEntities(2.0f, 40);
                    spawnedAnvils.add(anvil);
                    landedAnvilBlocks.add(groundPos);
                }
            }
        }

        world.playSound(null, bounds.centerX(), floorY, bounds.centerZ(), SoundEvents.BLOCK_ANVIL_FALL, SoundCategory.BLOCKS, 1.0f, 0.8f);
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        this.ticksElapsed++;
        int durationTicks = getDurationTicks(game);

        // Wind-up warning period (first 24 ticks / ~1.2s): give players time to react!
        if (!this.firstWaveSpawned) {
            if (this.ticksElapsed < 24) {
                if (this.ticksElapsed % 6 == 0) {
                    float secsLeft = Math.max(0.1f, (24 - this.ticksElapsed) / 20.0f);
                    for (ServerPlayerEntity p : game.getLivingPlayers()) {
                        p.sendMessage(Text.literal(String.format(java.util.Locale.ROOT, "§e⚠ Anvils incoming in §c%.1fs§e! Look for a gap!", secsLeft)), true);
                        p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.7f, 1.2f + (this.ticksElapsed * 0.03f));
                    }
                }
                return;
            }

            // Spawn first wave after 1.2s telegraph!
            this.firstWaveSpawned = true;
            spawnAnvilWave(game, world, 0);
            for (ServerPlayerEntity p : game.getLivingPlayers()) {
                p.sendMessage(Text.literal("§c§l💥 ANVILS FALLING! DODGE!"), true);
            }
        }

        // If round is long (>= 6.5s) and enough time has elapsed, trigger second staggered wave
        if (durationTicks >= 130 && !this.secondWaveSpawned && remainingTicks <= durationTicks - 70 && remainingTicks > 25) {
            this.secondWaveSpawned = true;
            cleanLandedAnvilBlocks(world);
            spawnAnvilWave(game, world, 1);
        }
    }

    private void cleanLandedAnvilBlocks(ServerWorld world) {
        if (world == null) return;
        for (BlockPos pos : landedAnvilBlocks) {
            if (world.getBlockState(pos).isOf(Blocks.DAMAGED_ANVIL) || world.getBlockState(pos).isOf(Blocks.ANVIL) || world.getBlockState(pos).isOf(Blocks.CHIPPED_ANVIL)) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
            }
        }
    }

    @Override
    public boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount, MicroPartyMinigame game) {
        if (source.isOf(DamageTypes.FALLING_ANVIL) || source.isOf(DamageTypes.FALLING_BLOCK)) {
            game.getTracker().recordHazardHit(player.getUuid());
            game.getTracker().setPassedCurrentRound(player.getUuid(), false);
            player.sendMessage(Text.literal("§c💥 Clang! An anvil crushed you!"), true);
            player.playSoundToPlayer(SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.0f, 1.0f);
            return false; // Prevent lethal damage, round resolution deducts life
        }
        return false;
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().hasPassedCurrentRound(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        ServerWorld world = game.getWorld();
        for (FallingBlockEntity anvil : spawnedAnvils) {
            if (anvil != null && anvil.isAlive()) {
                anvil.discard();
            }
        }
        spawnedAnvils.clear();

        // Complete arena sweep for any landed anvil blocks
        if (world != null && game.getMapConfig() != null) {
            MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
            int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());

            BlockPos.iterate(
                new BlockPos(bounds.minX() - 2, floorY - 1, bounds.minZ() - 2),
                new BlockPos(bounds.maxX() + 2, floorY + 4, bounds.maxZ() + 2)
            ).forEach(pos -> {
                if (world.getBlockState(pos).isOf(Blocks.DAMAGED_ANVIL) || world.getBlockState(pos).isOf(Blocks.ANVIL) || world.getBlockState(pos).isOf(Blocks.CHIPPED_ANVIL)) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                }
            });
        }
        landedAnvilBlocks.clear();
    }
}
