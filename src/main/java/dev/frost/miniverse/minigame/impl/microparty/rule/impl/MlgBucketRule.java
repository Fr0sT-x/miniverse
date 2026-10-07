package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.*;

public class MlgBucketRule implements MicroRule {
    private final Set<UUID> passedPlayers = new HashSet<>();
    private final Set<UUID> failedPlayers = new HashSet<>();

    @Override
    public String id() {
        return "mlg_bucket";
    }

    @Override
    public String name() {
        return "MLG Water Drop";
    }

    @Override
    public String description() {
        return "Place your water bucket right before landing to survive the high fall!";
    }

    @Override
    public Text title() {
        return Text.literal("MLG WATER DROP!").formatted(Formatting.AQUA, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Water drop before you land!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public double minDurationSeconds() {
        return 4.0;
    }

    private final Set<BlockPos> placedWaterBlocks = new HashSet<>();

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.passedPlayers.clear();
        this.failedPlayers.clear();
        this.placedWaterBlocks.clear();

        ServerWorld world = game.getWorld();
        if (world == null) return;

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.changeGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.getInventory().setStack(0, new ItemStack(Items.WATER_BUCKET));
            p.getInventory().selectedSlot = 0;
            p.currentScreenHandler.sendContentUpdates();

            // Slightly elevate player off the ground so ground collision/friction doesn't swallow upward velocity
            p.teleport(world, p.getX(), p.getY() + 0.25, p.getZ(), Set.of(), p.getYaw(), p.getPitch());
            p.setVelocity(new Vec3d(0, 1.95, 0));
            p.velocityModified = true;
            p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));

            p.playSoundToPlayer(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
        }
    }

    private void markPassed(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (this.passedPlayers.add(player.getUuid())) {
            this.failedPlayers.remove(player.getUuid());
            player.sendMessage(Text.literal("§a§l✔ MLG Clutch!"), true);
            player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
            if (world != null && pos != null) {
                world.spawnParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (this.passedPlayers.contains(p.getUuid())) {
                continue;
            }

            BlockPos feet = p.getBlockPos();
            BlockPos below = feet.down();

            // Track any water near player feet
            if (world.getBlockState(feet).isOf(Blocks.WATER)) {
                this.placedWaterBlocks.add(feet.toImmutable());
            }
            if (world.getBlockState(below).isOf(Blocks.WATER)) {
                this.placedWaterBlocks.add(below.toImmutable());
            }

            // Check if player landed safely in water
            boolean inWater = p.isInsideWaterOrBubbleColumn()
                || world.getBlockState(feet).isOf(Blocks.WATER)
                || world.getBlockState(below).isOf(Blocks.WATER)
                || world.getBlockState(feet.north()).isOf(Blocks.WATER)
                || world.getBlockState(feet.south()).isOf(Blocks.WATER)
                || world.getBlockState(feet.east()).isOf(Blocks.WATER)
                || world.getBlockState(feet.west()).isOf(Blocks.WATER);

            // Check if player has emptied their bucket (meaning they placed water!) and reached ground
            boolean bucketEmptied = p.getInventory().contains(new ItemStack(Items.BUCKET))
                || p.getMainHandStack().isOf(Items.BUCKET)
                || p.getOffHandStack().isOf(Items.BUCKET);
            boolean nearGround = p.isOnGround() || p.getVelocity().y >= -0.2;

            if (inWater || (bucketEmptied && nearGround)) {
                markPassed(p, world, feet);

                // Register placed water into block manager so it is cleanly restored on round end
                if (world.getBlockState(feet).isOf(Blocks.WATER)) {
                    game.getBlockManager().setTemporaryBlock(world, feet, Blocks.WATER.getDefaultState());
                }
                if (world.getBlockState(below).isOf(Blocks.WATER)) {
                    game.getBlockManager().setTemporaryBlock(world, below, Blocks.WATER.getDefaultState());
                }
            }
        }
    }

    @Override
    public boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount, MicroPartyMinigame game) {
        if (source.isOf(DamageTypes.FALL)) {
            // If player already clutched or has water near their landing point, negate fall damage and count pass
            if (this.passedPlayers.contains(player.getUuid())) {
                player.setHealth(20.0f);
                return true; // Intercept residual ground impact damage!
            }
            BlockPos feet = player.getBlockPos();
            ServerWorld world = player.getServerWorld();
            boolean nearWater = world.getBlockState(feet).isOf(Blocks.WATER)
                || world.getBlockState(feet.down()).isOf(Blocks.WATER)
                || world.getBlockState(feet.north()).isOf(Blocks.WATER)
                || world.getBlockState(feet.south()).isOf(Blocks.WATER)
                || world.getBlockState(feet.east()).isOf(Blocks.WATER)
                || world.getBlockState(feet.west()).isOf(Blocks.WATER)
                || player.isInsideWaterOrBubbleColumn();
            boolean bucketEmptied = player.getInventory().contains(new ItemStack(Items.BUCKET))
                || player.getMainHandStack().isOf(Items.BUCKET)
                || player.getOffHandStack().isOf(Items.BUCKET);

            if (nearWater || bucketEmptied) {
                markPassed(player, world, feet);
                player.setHealth(20.0f);
                return true;
            }
            this.failedPlayers.add(player.getUuid());
            player.sendMessage(Text.literal("§c§l✖ Missed the MLG!"), true);
            player.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_BIG_FALL, SoundCategory.PLAYERS, 0.9f, 0.8f);
            player.setHealth(20.0f);
            return true; // Intercept lethal damage
        }
        return false;
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return this.passedPlayers.contains(player.getUuid());
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, World world, Hand hand, MicroPartyMinigame game) {
        if (game.isEliminated(player.getUuid()) || !game.getTracker().isAlive(player.getUuid())) {
            return ActionResult.PASS;
        }
        ItemStack held = player.getStackInHand(hand);
        if (held.isOf(Items.WATER_BUCKET) && world instanceof ServerWorld serverWorld) {
            markPassed(player, serverWorld, player.getBlockPos());
        }
        return ActionResult.PASS;
    }

    @Override
    public ActionResult onUseBlock(ServerPlayerEntity player, World world, Hand hand, BlockHitResult hitResult, MicroPartyMinigame game) {
        if (game.isEliminated(player.getUuid()) || !game.getTracker().isAlive(player.getUuid())) {
            return ActionResult.PASS;
        }

        ItemStack held = player.getStackInHand(hand);
        if (held.isOf(Items.WATER_BUCKET) && world instanceof ServerWorld serverWorld) {
            BlockPos hitPos = hitResult.getBlockPos();
            BlockState hitState = world.getBlockState(hitPos);
            BlockPos targetPos;
            if (hitState.isReplaceable() || hitState.isAir() || hitState.isLiquid()) {
                targetPos = hitPos;
            } else {
                targetPos = hitPos.offset(hitResult.getSide());
            }

            BlockState targetState = world.getBlockState(targetPos);
            if (targetState.isReplaceable() || targetState.isAir() || targetState.isLiquid()) {
                game.getBlockManager().setTemporaryBlock(serverWorld, targetPos, Blocks.WATER.getDefaultState());
                this.placedWaterBlocks.add(targetPos.toImmutable());

                if (!player.isCreative()) {
                    player.setStackInHand(hand, new ItemStack(Items.BUCKET));
                }
                serverWorld.playSound(null, targetPos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0f, 1.0f);

                // Successfully executed water clutch placement!
                markPassed(player, serverWorld, targetPos);
                return ActionResult.SUCCESS;
            }
        } else if (held.isOf(Items.BUCKET) && world instanceof ServerWorld serverWorld) {
            BlockPos hitPos = hitResult.getBlockPos();
            BlockPos offsetPos = hitPos.offset(hitResult.getSide());
            BlockPos feetPos = player.getBlockPos();
            BlockPos belowFeetPos = feetPos.down();

            BlockPos waterPos = null;
            if (world.getBlockState(hitPos).isOf(Blocks.WATER)) {
                waterPos = hitPos;
            } else if (world.getBlockState(offsetPos).isOf(Blocks.WATER)) {
                waterPos = offsetPos;
            } else if (world.getBlockState(feetPos).isOf(Blocks.WATER)) {
                waterPos = feetPos;
            } else if (world.getBlockState(belowFeetPos).isOf(Blocks.WATER)) {
                waterPos = belowFeetPos;
            }

            if (waterPos != null) {
                world.setBlockState(waterPos, Blocks.AIR.getDefaultState());
                if (!player.isCreative()) {
                    player.setStackInHand(hand, new ItemStack(Items.WATER_BUCKET));
                }
                serverWorld.playSound(null, waterPos, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1.0f, 1.0f);
                markPassed(player, serverWorld, waterPos);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            p.changeGameMode(GameMode.ADVENTURE);
        }

        // Clean up any water blocks placed or spread during the round
        ServerWorld world = game.getWorld();
        if (world != null) {
            for (BlockPos pos : this.placedWaterBlocks) {
                if (world.getBlockState(pos).isOf(Blocks.WATER)) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                }
            }
        }
        this.placedWaterBlocks.clear();
        this.passedPlayers.clear();
        this.failedPlayers.clear();
    }
}
