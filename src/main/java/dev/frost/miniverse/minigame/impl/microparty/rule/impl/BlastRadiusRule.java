package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
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
    private double requiredSafeDistance = 10.0;
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
        return instruction(null);
    }

    @Override
    public Text instruction(MicroPartyMinigame game) {
        return Text.literal("Run at least " + String.format(java.util.Locale.ROOT, "%.1fm", this.requiredSafeDistance) + " away from the bomb!").formatted(Formatting.YELLOW);
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
    public void onPrepare(MicroPartyMinigame game, MinecraftServer server) {
        if (game != null) {
            MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
            double halfSpan = Math.min(bounds.width(), bounds.depth()) / 2.0;
            // Dynamic larger safe distance varying from 9.0m to 12.0m (capped to fit arena with margin)
            double maxAllowed = Math.max(7.5, halfSpan - 1.5);
            this.requiredSafeDistance = Math.min(maxAllowed, 9.0 + (new java.util.Random().nextDouble() * 3.0));
        } else {
            this.requiredSafeDistance = 10.0;
        }
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        this.centerX = bounds.centerX() + 0.5;
        this.centerY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
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
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world != null) {
            if (remainingTicks % 4 == 0) {
                world.spawnParticles(ParticleTypes.SMOKE, this.centerX, this.centerY + 1.0, this.centerZ, 3, 0.2, 0.2, 0.2, 0.02);
                world.spawnParticles(ParticleTypes.FLAME, this.centerX, this.centerY + 1.0, this.centerZ, 1, 0.1, 0.1, 0.1, 0.01);
            }

            // Draw visible danger perimeter ring on the arena floor
            for (int deg = 0; deg < 360; deg += 18) {
                double rad = Math.toRadians(deg);
                double px = this.centerX + Math.cos(rad) * this.requiredSafeDistance;
                double pz = this.centerZ + Math.sin(rad) * this.requiredSafeDistance;
                world.spawnParticles(ParticleTypes.SMALL_FLAME, px, this.centerY + 0.1, pz, 1, 0, 0, 0, 0);
            }
        }

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            double dist = Math.sqrt(p.squaredDistanceTo(this.centerX, this.centerY, this.centerZ));
            if (dist >= this.requiredSafeDistance) {
                p.sendMessage(Text.literal(String.format(java.util.Locale.ROOT, "§a§l✔ Safe: %.1fm §7(Border: %.1fm)", dist, this.requiredSafeDistance)), true);
            } else {
                p.sendMessage(Text.literal(String.format(java.util.Locale.ROOT, "§c§l❌ Danger: %.1fm §7(Need >= %.1fm!)", dist, this.requiredSafeDistance)), true);
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return player.squaredDistanceTo(this.centerX, this.centerY, this.centerZ) >= (this.requiredSafeDistance * this.requiredSafeDistance);
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
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
