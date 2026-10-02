package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyArenaHelper;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.entity.TntEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class BlastRadiusRule implements MicroRule {
    private static final double REQUIRED_SAFE_DISTANCE = 7.5;
    private TntEntity primedTnt = null;
    private double centerX = 0.0;
    private double centerY = 100.0;
    private double centerZ = 0.0;

    @Override
    public String id() {
        return "blast_radius";
    }

    @Override
    public String name() {
        return "Blast Radius";
    }

    @Override
    public String description() {
        return "Run as far away from the ticking center TNT as possible.";
    }

    @Override
    public Text title() {
        return Text.literal("BLAST RADIUS!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Run away from the ticking bomb in the center!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        MicroFrenzyArenaHelper.ArenaBounds2D bounds = MicroFrenzyArenaHelper.getBounds2D(game.getMapConfig());
        this.centerX = bounds.centerX() + 0.5;
        this.centerY = MicroFrenzyArenaHelper.getFloorY(game.getMapConfig());
        this.centerZ = bounds.centerZ() + 0.5;

        MapPosition explicitCenter = game.getMapConfig().arenaCenter();
        if (explicitCenter != null) {
            this.centerX = explicitCenter.x() + 0.5;
            this.centerY = explicitCenter.y();
            this.centerZ = explicitCenter.z() + 0.5;
        }

        // Spawn central primed TNT
        this.primedTnt = new TntEntity(world, this.centerX, this.centerY, this.centerZ, null);
        this.primedTnt.setFuse(getDurationTicks(game));
        world.spawnEntity(this.primedTnt);
        world.playSound(null, this.centerX, this.centerY, this.centerZ, SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public void onTick(MicroFrenzyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world != null && remainingTicks % 4 == 0) {
            world.spawnParticles(ParticleTypes.SMOKE, this.centerX, this.centerY + 1.0, this.centerZ, 3, 0.2, 0.2, 0.2, 0.02);
            world.spawnParticles(ParticleTypes.FLAME, this.centerX, this.centerY + 1.0, this.centerZ, 1, 0.1, 0.1, 0.1, 0.01);
        }

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            double dist = Math.sqrt(p.squaredDistanceTo(this.centerX, this.centerY, this.centerZ));
            if (dist >= REQUIRED_SAFE_DISTANCE) {
                p.sendMessage(Text.literal("§aSafe: " + String.format("%.1f", dist) + "m §7(Safe Zone)"), true);
            } else {
                p.sendMessage(Text.literal("§cDanger: " + String.format("%.1f", dist) + "m §7(Min " + REQUIRED_SAFE_DISTANCE + "m!)"), true);
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return player.squaredDistanceTo(this.centerX, this.centerY, this.centerZ) >= (REQUIRED_SAFE_DISTANCE * REQUIRED_SAFE_DISTANCE);
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        if (this.primedTnt != null && this.primedTnt.isAlive()) {
            this.primedTnt.discard();
        }
        this.primedTnt = null;

        ServerWorld world = game.getWorld();
        if (world != null) {
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, this.centerX, this.centerY + 0.5, this.centerZ, 1, 0, 0, 0, 0);
            world.playSound(null, this.centerX, this.centerY, this.centerZ, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.2f, 1.0f);
        }
    }
}
