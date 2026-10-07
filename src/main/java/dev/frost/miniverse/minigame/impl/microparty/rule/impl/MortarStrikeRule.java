package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.*;

public class MortarStrikeRule implements MicroRule {
    public record MortarReticle(double x, double y, double z, double radius, int detonateTick) {}

    private final Set<UUID> hitPlayers = new HashSet<>();
    private final List<MortarReticle> activeReticles = new ArrayList<>();
    private final Random random = new Random();
    private boolean wave2Spawned = false;
    private boolean wave3Spawned = false;

    @Override
    public String id() {
        return "mortar_strike";
    }

    @Override
    public String name() {
        return "Mortar Strike";
    }

    @Override
    public String description() {
        return "Dodge the red blast reticles before artillery strikes the floor!";
    }

    @Override
    public Text title() {
        return Text.literal("MORTAR STRIKE!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Avoid the red blast zones!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public double minDurationSeconds() {
        return 4.0;
    }

    private void spawnReticleWave(MicroPartyMinigame game, int detonateInTicks) {
        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        int margin = Math.max(1, bounds.width() / 10);

        // Substantially increased number of mortar zones (14 to 22 zones per wave)
        int count = 14 + random.nextInt(9);
        double[] possibleRadii = { 2.0, 2.8, 3.5, 4.2, 5.5 };

        for (int i = 0; i < count; i++) {
            double rx = bounds.minX() + margin + random.nextDouble() * Math.max(1, bounds.width() - 2 * margin);
            double rz = bounds.minZ() + margin + random.nextDouble() * Math.max(1, bounds.depth() - 2 * margin);
            double zoneRadius = possibleRadii[random.nextInt(possibleRadii.length)];
            this.activeReticles.add(new MortarReticle(rx, floorY + 0.1, rz, zoneRadius, detonateInTicks));
        }
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.hitPlayers.clear();
        this.activeReticles.clear();
        this.wave2Spawned = false;
        this.wave3Spawned = false;

        int totalTicks = getDurationTicks(game);
        int wave1Detonate = Math.max(25, (int)(totalTicks * 0.55));
        spawnReticleWave(game, wave1Detonate);
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        int totalTicks = getDurationTicks(game);

        // Wave 2 barrage
        if (!this.wave2Spawned && remainingTicks <= (int)(totalTicks * 0.65) && remainingTicks > 25) {
            this.wave2Spawned = true;
            spawnReticleWave(game, Math.max(10, (int)(totalTicks * 0.20)));
        }

        // Wave 3 barrage if duration permits
        if (!this.wave3Spawned && remainingTicks <= (int)(totalTicks * 0.35) && remainingTicks > 15) {
            this.wave3Spawned = true;
            spawnReticleWave(game, Math.max(3, remainingTicks - 20));
        }

        DustParticleEffect redDust = new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.1f), 1.2f);
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());

        if (remainingTicks % 8 == 0 && !this.activeReticles.isEmpty()) {
            world.playSound(null, bounds.centerX(), floorY, bounds.centerZ(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.8f, 1.8f);
        }

        Iterator<MortarReticle> it = this.activeReticles.iterator();
        while (it.hasNext()) {
            MortarReticle reticle = it.next();

            // Draw ring of red dust particles scaled to this reticle's custom radius
            double r = reticle.radius();
            int degStep = r > 3.5 ? 15 : 20;
            for (int deg = 0; deg < 360; deg += degStep) {
                double rad = Math.toRadians(deg);
                double px = reticle.x() + Math.cos(rad) * r;
                double pz = reticle.z() + Math.sin(rad) * r;
                world.spawnParticles(redDust, px, reticle.y(), pz, 1, 0, 0, 0, 0);
            }
            world.spawnParticles(ParticleTypes.SMOKE, reticle.x(), reticle.y(), reticle.z(), 1, 0.1, 0.1, 0.1, 0.01);

            // Check detonation
            if (remainingTicks == reticle.detonateTick()) {
                world.spawnParticles(ParticleTypes.EXPLOSION, reticle.x(), reticle.y() + 0.5, reticle.z(), 2, 0.2, 0.2, 0.2, 0.05);
                world.spawnParticles(ParticleTypes.FLAME, reticle.x(), reticle.y() + 0.2, reticle.z(), 12, 0.5, 0.2, 0.5, 0.08);
                world.playSound(null, reticle.x(), reticle.y(), reticle.z(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.0f, 1.2f);

                for (ServerPlayerEntity p : game.getLivingPlayers()) {
                    double distSq = (p.getX() - reticle.x()) * (p.getX() - reticle.x()) + (p.getZ() - reticle.z()) * (p.getZ() - reticle.z());
                    if (distSq <= r * r && Math.abs(p.getY() - reticle.y()) <= 2.5) {
                        this.hitPlayers.add(p.getUuid());
                        game.getTracker().recordHazardHit(p.getUuid());
                        p.sendMessage(Text.literal("§c§l💥 HIT BY MORTAR!"), true);
                        p.setVelocity(new Vec3d(0, 0.5, 0));
                        p.velocityModified = true;
                        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                    }
                }
                it.remove();
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return !this.hitPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        this.activeReticles.clear();
        this.hitPlayers.clear();
    }
}
