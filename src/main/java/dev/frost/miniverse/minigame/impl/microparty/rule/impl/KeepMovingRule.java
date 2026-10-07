package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class KeepMovingRule implements MicroRule {
    private final Map<UUID, Vec3d> lastPositions = new HashMap<>();
    private final Map<UUID, Integer> stationaryTicks = new HashMap<>();
    private int ticksElapsed = 0;

    @Override
    public String id() {
        return "keep_moving";
    }

    @Override
    public String name() {
        return "Don't Stop";
    }

    @Override
    public String description() {
        return "Keep sprinting! Once the acceleration buffer ends, standing still will fail.";
    }

    @Override
    public Text title() {
        return Text.literal("DON'T STOP!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Start sprinting! Do NOT stop moving!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 6;
    }

    @Override
    public double minDurationSeconds() {
        return 3.5;
    }

    @Override
    public void sendInitialActionBar(MicroPartyMinigame game, ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§a🏃 SPRINT! §eGrace period: §f1.5s"), true);
    }

    private int getGraceTicks(MicroPartyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        return Math.max(18, Math.round(30 * factor));
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.lastPositions.clear();
        this.stationaryTicks.clear();
        this.ticksElapsed = 0;

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            this.lastPositions.put(p.getUuid(), p.getPos());
            this.stationaryTicks.put(p.getUuid(), 0);
            game.getTracker().setPassedCurrentRound(p.getUuid(), true); // Pass unless they stop
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        this.ticksElapsed++;
        int graceTicks = getGraceTicks(game);
        boolean inGrace = this.ticksElapsed <= graceTicks;

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            UUID id = p.getUuid();
            Vec3d last = this.lastPositions.getOrDefault(id, p.getPos());
            double dist = Math.hypot(p.getX() - last.x, p.getZ() - last.z);

            if (inGrace) {
                // Buffer countdown
                if (this.ticksElapsed % 5 == 0) {
                    float secs = Math.max(0.1f, (graceTicks - this.ticksElapsed) / 20.0f);
                    p.sendMessage(Text.literal(String.format(Locale.ROOT, "§a🏃 START RUNNING! §eBuffer: §f%.1fs", secs)), true);
                }
                ServerWorld world = p.getServerWorld();
                world.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.1, 0.05, 0.1, 0.01);
                this.stationaryTicks.put(id, 0); // No penalties during grace window
            } else {
                // Active detection
                if (dist < 0.05) {
                    int stopped = this.stationaryTicks.merge(id, 1, Integer::sum);
                    if (stopped >= 10 && game.getTracker().hasPassedCurrentRound(id)) {
                        game.getTracker().setPassedCurrentRound(id, false);
                        p.sendMessage(Text.literal("§c❌ You stopped moving! (FAILED)"), true);
                        p.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.9f, 1.0f);
                        ServerWorld world = p.getServerWorld();
                        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                    }
                } else {
                    this.stationaryTicks.put(id, 0);
                    if (remainingTicks % 10 == 0) {
                        p.sendMessage(Text.literal("§a§l🏃 RUNNING! §7(Keep moving!)"), true);
                    }
                }
            }

            this.lastPositions.put(id, p.getPos());
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().hasPassedCurrentRound(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        this.lastPositions.clear();
        this.stationaryTicks.clear();
        this.ticksElapsed = 0;
    }
}
