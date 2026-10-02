package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyArenaHelper;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
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
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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
    public int getDurationTicks(MicroFrenzyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(7 * 20 * factor);
        return Math.max(70, standardTicks); // Clamped to at least 3.5 seconds
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        this.passedPlayers.clear();
        this.failedPlayers.clear();

        ServerWorld world = game.getWorld();
        if (world == null) return;

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            p.getInventory().setStack(0, new ItemStack(Items.WATER_BUCKET));
            p.getInventory().selectedSlot = 0;
            p.currentScreenHandler.sendContentUpdates();

            // Swift launch upward (~16 blocks) without teleporting
            p.setVelocity(new Vec3d(0, 1.85, 0));
            p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
            p.playSoundToPlayer(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
        }
    }

    @Override
    public void onTick(MicroFrenzyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (this.passedPlayers.contains(p.getUuid()) || this.failedPlayers.contains(p.getUuid())) {
                continue;
            }

            BlockPos feet = p.getBlockPos();
            BlockPos below = feet.down();

            // Check if player landed safely in water
            boolean inWater = p.isInsideWaterOrBubbleColumn()
                || world.getBlockState(feet).isOf(Blocks.WATER)
                || world.getBlockState(below).isOf(Blocks.WATER);

            if (inWater) {
                this.passedPlayers.add(p.getUuid());
                p.sendMessage(Text.literal("§a§l✔ MLG Clutch!"), true);
                p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                world.spawnParticles(ParticleTypes.SPLASH, p.getX(), p.getY(), p.getZ(), 15, 0.3, 0.3, 0.3, 0.1);

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
    public boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount, MicroFrenzyMinigame game) {
        if (source.isOf(DamageTypes.FALL)) {
            this.failedPlayers.add(player.getUuid());
            player.sendMessage(Text.literal("§c§l✖ Missed the MLG!"), true);
            player.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_BIG_FALL, SoundCategory.PLAYERS, 0.9f, 0.8f);
            return false; // Intercept lethal damage
        }
        return false;
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return this.passedPlayers.contains(player.getUuid()) && !this.failedPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
        }
        this.passedPlayers.clear();
        this.failedPlayers.clear();
    }
}
