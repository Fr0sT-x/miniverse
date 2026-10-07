package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.*;

public class SweeperBarRule implements MicroRule {
    private final Set<UUID> hitPlayers = new HashSet<>();
    private final List<DisplayEntity.BlockDisplayEntity> beamSegments = new ArrayList<>();
    private double currentAngle = 0.0;
    private int ticksElapsed = 0;
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
        return 9;
    }

    @Override
    public double minDurationSeconds() {
        return 5.5;
    }

    @Override
    public void sendInitialActionBar(MicroPartyMinigame game, ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§e⚡ Sweeper charging... §c1.0s"), true);
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        cleanEntities();
        this.hitPlayers.clear();
        this.ticksElapsed = 0;
        this.currentAngle = random.nextDouble() * Math.PI * 2;

        ServerWorld world = game.getWorld();
        if (world == null) return;

        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        double cx = (bounds.minX() + bounds.maxX()) / 2.0;
        double cz = (bounds.minZ() + bounds.maxZ()) / 2.0;
        double armRadius = Math.max(5.0, Math.min(bounds.width(), bounds.depth()) / 2.0);
        double beamY = floorY + 0.35;

        double cos = Math.cos(this.currentAngle);
        double sin = Math.sin(this.currentAngle);

        // Spawn solid laser beam segments along the line
        int count = (int) Math.ceil(armRadius / 0.75);
        for (int i = 0; i <= count; i++) {
            double d = Math.min(armRadius, i * 0.75);
            DisplayEntity.BlockDisplayEntity display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            display.setBlockState(Blocks.REDSTONE_BLOCK.getDefaultState());
            display.setTransformation(new AffineTransformation(null, null, new Vector3f(0.38f, 0.38f, 0.38f), null));
            display.setPosition(cx + cos * d - 0.19, beamY, cz + sin * d - 0.19);
            world.spawnEntity(display);
            this.beamSegments.add(display);
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        this.ticksElapsed++;
        boolean isWindUp = this.ticksElapsed <= 20;

        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());

        double cx = (bounds.minX() + bounds.maxX()) / 2.0;
        double cz = (bounds.minZ() + bounds.maxZ()) / 2.0;
        double armRadius = Math.max(5.0, Math.min(bounds.width(), bounds.depth()) / 2.0);
        double beamY = floorY + 0.35;

        if (isWindUp) {
            // Wind-up: beam stays stationary while charging
            if (this.ticksElapsed % 5 == 0) {
                for (ServerPlayerEntity p : game.getLivingPlayers()) {
                    p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.7f, 1.0f + (this.ticksElapsed * 0.04f));
                    float secsLeft = Math.max(0.1f, (20 - this.ticksElapsed) / 20.0f);
                    p.sendMessage(Text.literal(String.format(java.util.Locale.ROOT, "§e⚡ Sweeper charging... §c%.1fs", secsLeft)), true);
                }
            }
            return; // No rotation and no knockback during wind-up
        }

        // Active sweeping phase: rotate the beam
        float factor = game.getSpeedFactor();
        double angularSpeed = 0.10 * (1.0f / Math.max(0.6f, factor));
        this.currentAngle += angularSpeed;

        double cos = Math.cos(this.currentAngle);
        double sin = Math.sin(this.currentAngle);
        double tipX = cx + cos * armRadius;
        double tipZ = cz + sin * armRadius;

        // Reposition beam segments along the new angle — no trailing particles!
        int count = this.beamSegments.size();
        for (int i = 0; i < count; i++) {
            double d = Math.min(armRadius, i * 0.75);
            DisplayEntity.BlockDisplayEntity seg = this.beamSegments.get(i);
            if (seg != null && seg.isAlive()) {
                seg.setPosition(cx + cos * d - 0.19, beamY, cz + sin * d - 0.19);
            }
        }

        // Center hub & tip particle
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
                    game.getTracker().recordHazardHit(p.getUuid());
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

    private void cleanEntities() {
        for (DisplayEntity.BlockDisplayEntity seg : this.beamSegments) {
            if (seg != null && seg.isAlive()) {
                seg.discard();
            }
        }
        this.beamSegments.clear();
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        cleanEntities();
        this.hitPlayers.clear();
    }
}
