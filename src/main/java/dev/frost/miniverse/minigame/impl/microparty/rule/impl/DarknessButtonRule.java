package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMapConfig;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DarknessButtonRule implements MicroRule {
    private final Random random = new Random();
    private final List<BlockPos> buttonPositions = new ArrayList<>();
    private final List<BlockPos> pedestalPositions = new ArrayList<>();
    private boolean darknessApplied = false;
    private int elapsedTicks = 0;

    @Override
    public String id() {
        return "darkness_button";
    }

    @Override
    public String name() {
        return "Blind Button";
    }

    @Override
    public String description() {
        return "Memorize the pedestal position in the flash and press the button in the dark!";
    }

    @Override
    public Text title() {
        return Text.literal("FIND THE BUTTON!").formatted(Formatting.DARK_PURPLE, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Memorize the spot and press the button in the dark!").formatted(Formatting.GOLD);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public double minDurationSeconds() {
        return 5.0;
    }

    @Override
    public boolean isApplicable(MicroPartyMapConfig mapConfig) {
        return true;
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.buttonPositions.clear();
        this.pedestalPositions.clear();
        this.darknessApplied = false;
        this.elapsedTicks = 0;

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            game.getTracker().setPassedCurrentRound(p.getUuid(), false);
        }

        ServerWorld world = game.getWorld();
        if (world == null) return;

        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        int cx = bounds.centerX();
        int cz = bounds.centerZ();

        // Generate 4 pedestals spread across the arena
        int[][] quadrantOffsets = new int[][] {
            { 1,  1 },
            {-1,  1 },
            { 1, -1 },
            {-1, -1 }
        };

        int maxDist = Math.max(3, Math.min(bounds.width(), bounds.depth()) / 3);
        for (int[] q : quadrantOffsets) {
            int dist = 3 + random.nextInt(Math.max(1, maxDist - 2));
            int x = cx + (q[0] * dist) + (random.nextInt(3) - 1);
            int z = cz + (q[1] * dist) + (random.nextInt(3) - 1);

            // Clamp inside arena bounds
            x = Math.max(bounds.minX() + 1, Math.min(bounds.maxX() - 1, x));
            z = Math.max(bounds.minZ() + 1, Math.min(bounds.maxZ() - 1, z));

            BlockPos pedestalPos = new BlockPos(x, floorY, z);
            BlockPos buttonPos = pedestalPos.up();

            // Set pedestal and button
            game.getBlockManager().setTemporaryBlock(world, pedestalPos, Blocks.POLISHED_ANDESITE.getDefaultState());
            BlockState buttonState = Blocks.STONE_BUTTON.getDefaultState().with(Properties.BLOCK_FACE, BlockFace.FLOOR);
            game.getBlockManager().setTemporaryBlock(world, buttonPos, buttonState);

            this.pedestalPositions.add(pedestalPos);
            this.buttonPositions.add(buttonPos);
        }

        // Split-second reveal sound cue
        world.playSound(null, cx, floorY, cz, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.3f);
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        this.elapsedTicks++;
        ServerWorld world = game.getWorld();
        if (world == null) return;

        // Phase 1 (First 16 ticks ~ 0.8s): Bright visual beacons so players spot the pedestals
        if (this.elapsedTicks <= 16) {
            for (BlockPos bPos : this.buttonPositions) {
                world.spawnParticles(ParticleTypes.END_ROD, bPos.getX() + 0.5, bPos.getY() + 0.3, bPos.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0.02);
                world.spawnParticles(ParticleTypes.GLOW, bPos.getX() + 0.5, bPos.getY() + 0.5, bPos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.0);
            }
            return;
        }

        // Phase 2: Darkness descends!
        if (!this.darknessApplied) {
            this.darknessApplied = true;
            for (ServerPlayerEntity p : game.getLivingPlayers()) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, remainingTicks + 40, 0, false, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, remainingTicks + 40, 0, false, false, false));
                p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0f, 0.8f);
                p.playSoundToPlayer(SoundEvents.AMBIENT_CAVE.value(), SoundCategory.PLAYERS, 0.9f, 0.8f);
                p.sendMessage(Text.literal("§8§l[DARKNESS] §eNavigate and press a button!"), true);
            }
        }

        // Heartbeat ambient pulse in darkness
        if (this.elapsedTicks % 25 == 0) {
            for (ServerPlayerEntity p : game.getLivingPlayers()) {
                if (!game.getTracker().hasPassedCurrentRound(p.getUuid())) {
                    p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 0.6f, 1.0f);
                }
            }
        }

        // Fallback: check if any button was pressed via redstone state
        for (BlockPos bPos : this.buttonPositions) {
            BlockState state = world.getBlockState(bPos);
            if (state.contains(Properties.POWERED) && Boolean.TRUE.equals(state.get(Properties.POWERED))) {
                for (ServerPlayerEntity p : game.getLivingPlayers()) {
                    if (p.squaredDistanceTo(bPos.getX() + 0.5, bPos.getY() + 0.5, bPos.getZ() + 0.5) <= 16.0) {
                        markPassed(p, bPos, world, game);
                    }
                }
            }
        }
    }

    @Override
    public ActionResult onUseBlock(ServerPlayerEntity player, World world, Hand hand, BlockHitResult hitResult, MicroPartyMinigame game) {
        if (hand != Hand.MAIN_HAND || game.isEliminated(player.getUuid()) || !game.getTracker().isAlive(player.getUuid())) {
            return ActionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        if (this.buttonPositions.contains(pos)) {
            markPassed(player, pos, world, game);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private void markPassed(ServerPlayerEntity player, BlockPos pos, World world, MicroPartyMinigame game) {
        if (game.getTracker().hasPassedCurrentRound(player.getUuid())) {
            return;
        }

        game.getTracker().setPassedCurrentRound(player.getUuid(), true);
        player.removeStatusEffect(StatusEffects.DARKNESS);
        player.removeStatusEffect(StatusEffects.BLINDNESS);
        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 1.4f);
        player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
        player.sendMessage(Text.literal("§a§l✔ Button Pressed! Passed!"), true);

        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().hasPassedCurrentRound(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.removeStatusEffect(StatusEffects.DARKNESS);
            p.removeStatusEffect(StatusEffects.BLINDNESS);
        }

        if (game.getWorld() != null) {
            game.getBlockManager().restoreAll(game.getWorld());
        }

        this.buttonPositions.clear();
        this.pedestalPositions.clear();
    }
}
