package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class SweeperBarRule implements MicroRule {
    private final Set<UUID> hitPlayers = new HashSet<>();
    private double currentAngle = 0.0;
    private final Random random = new Random();

    @Override
    public String id() {
        return "sweeper_bar";
    }

    @Override
    public String name() {
        return "Jump the Sweeper";
    }

    @Override
    public String description() {
        return "Jump over the rotating sweeper beam before it sweeps you off your feet!";
    }

    @Override
    public Text title() {
        return Text.literal("JUMP THE SWEEPER!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Jump over the rotating beam!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public int getDurationTicks(MicroPartyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(7 * 20 * factor);
        return Math.max(70, standardTicks); // Clamped to at least 3.5 seconds
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.hitPlayers.clear();
        this.currentAngle = random.nextDouble() * Math.PI * 2;
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());

        double cx = (bounds.minX() + bounds.maxX()) / 2.0;
        double cz = (bounds.minZ() + bounds.maxZ()) / 2.0;
        double armRadius = Math.max(5.0, Math.min(bounds.width(), bounds.depth()) / 2.0);

        float factor = game.getSpeedFactor();
        double angularSpeed = 0.10 * (1.0f / Math.max(0.6f, factor));
        this.currentAngle += angularSpeed;

        double cos = Math.cos(this.currentAngle);
        double sin = Math.sin(this.currentAngle);
        double tipX = cx + cos * armRadius;
        double tipZ = cz + sin * armRadius;
        double beamY = floorY + 0.35;

        // Render beam particles
        for (double d = 0; d <= armRadius; d += 0.7) {
            double px = cx + cos * d;
            double pz = cz + sin * d;
            world.spawnParticles(ParticleTypes.FLAME, px, beamY, pz, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.CRIT, px, beamY, pz, 1, 0, 0, 0, 0);
        }

        // Center hub particle
        world.spawnParticles(ParticleTypes.LAVA, cx, beamY + 0.2, cz, 1, 0, 0, 0, 0);

        // Check players
        double dx = tipX - cx;
        double dz = tipZ - cz;
        double lenSq = armRadius * armRadius;

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (this.hitPlayers.contains(p.getUuid())) continue;

            // Distance from player to 2D line segment
            double t = Math.max(0.0, Math.min(1.0, ((p.getX() - cx) * dx + (p.getZ() - cz) * dz) / lenSq));
            double closeX = cx + t * dx;
            double closeZ = cz + t * dz;
            double distSq = (p.getX() - closeX) * (p.getX() - closeX) + (p.getZ() - closeZ) * (p.getZ() - closeZ);

            if (distSq <= 1.3 * 1.3) {
                // If player is on the ground or low height -> SWEPT
                if (p.getY() <= floorY + 0.45) {
                    this.hitPlayers.add(p.getUuid());
                    p.sendMessage(Text.literal("§c§l💥 SWEPT BY BEAM!"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.0f, 0.8f);

                    Vec3d fling = new Vec3d(-sin * 0.7, 0.45, cos * 0.7);
                    p.setVelocity(fling);
                    p.velocityModified = true;
                    p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                } else {
                    // Successfully cleared / jumping over
                    p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.4f, 1.8f);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return !this.hitPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        this.hitPlayers.clear();
    }
}
