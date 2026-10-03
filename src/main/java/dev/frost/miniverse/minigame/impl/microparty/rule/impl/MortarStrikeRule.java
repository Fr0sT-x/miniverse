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
        return 8;
    }

    @Override
    public int getDurationTicks(MicroPartyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(8 * 20 * factor);
        return Math.max(80, standardTicks); // Clamped to at least 4.0 seconds
    }

    private void spawnReticleWave(MicroPartyMinigame game, int detonateInTicks) {
        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        int margin = Math.max(2, bounds.width() / 6);

        int count = 3 + random.nextInt(4); // 3 to 6 mortar strikes
        for (int i = 0; i < count; i++) {
            double rx = bounds.minX() + margin + random.nextDouble() * Math.max(1, bounds.width() - 2 * margin);
            double rz = bounds.minZ() + margin + random.nextDouble() * Math.max(1, bounds.depth() - 2 * margin);
            this.activeReticles.add(new MortarReticle(rx, floorY + 0.1, rz, 2.5, detonateInTicks));
        }
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.hitPlayers.clear();
        this.activeReticles.clear();
        this.wave2Spawned = false;

        int totalTicks = getDurationTicks(game);
        int wave1Detonate = Math.max(25, totalTicks / 2);
        spawnReticleWave(game, wave1Detonate);
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        int totalTicks = getDurationTicks(game);

        // Spawn wave 2 at halfway mark if long enough
        if (!this.wave2Spawned && remainingTicks <= totalTicks / 2 && remainingTicks > 30) {
            this.wave2Spawned = true;
            spawnReticleWave(game, Math.max(5, remainingTicks - 35));
        }

        DustParticleEffect redDust = new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.1f), 1.2f);

        Iterator<MortarReticle> it = this.activeReticles.iterator();
        while (it.hasNext()) {
            MortarReticle reticle = it.next();

            // Draw ring of red dust particles
            double r = reticle.radius();
            for (int deg = 0; deg < 360; deg += 30) {
                double rad = Math.toRadians(deg);
                double px = reticle.x() + Math.cos(rad) * r;
                double pz = reticle.z() + Math.sin(rad) * r;
                world.spawnParticles(redDust, px, reticle.y(), pz, 1, 0, 0, 0, 0);
            }
            world.spawnParticles(ParticleTypes.SMOKE, reticle.x(), reticle.y(), reticle.z(), 1, 0.1, 0.1, 0.1, 0.01);

            // Audio warning
            if (remainingTicks % 8 == 0) {
                world.playSound(null, reticle.x(), reticle.y(), reticle.z(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.6f, 1.8f);
            }

            // Check detonation
            if (remainingTicks == reticle.detonateTick()) {
                world.spawnParticles(ParticleTypes.EXPLOSION, reticle.x(), reticle.y() + 0.5, reticle.z(), 2, 0.2, 0.2, 0.2, 0.05);
                world.spawnParticles(ParticleTypes.FLAME, reticle.x(), reticle.y() + 0.2, reticle.z(), 12, 0.5, 0.2, 0.5, 0.08);
                world.playSound(null, reticle.x(), reticle.y(), reticle.z(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.0f, 1.2f);

                for (ServerPlayerEntity p : game.getLivingPlayers()) {
                    double distSq = (p.getX() - reticle.x()) * (p.getX() - reticle.x()) + (p.getZ() - reticle.z()) * (p.getZ() - reticle.z());
                    if (distSq <= r * r && Math.abs(p.getY() - reticle.y()) <= 2.5) {
                        this.hitPlayers.add(p.getUuid());
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
