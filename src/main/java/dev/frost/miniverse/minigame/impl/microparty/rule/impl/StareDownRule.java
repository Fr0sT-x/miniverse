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

import java.util.*;

public class StareDownRule implements MicroRule {
    private final Map<UUID, Integer> stareTicks = new HashMap<>();
    private final Set<UUID> passedPlayers = new HashSet<>();

    @Override
    public String id() {
        return "stare_down";
    }

    @Override
    public String name() {
        return "Stare Down";
    }

    @Override
    public String description() {
        return "Lock eyes with another player by looking directly at them!";
    }

    @Override
    public Text title() {
        return Text.literal("STARE DOWN!").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Look directly at another player!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 6;
    }

    @Override
    public int getDurationTicks(MicroPartyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(6 * 20 * factor);
        return Math.max(60, standardTicks); // Clamped to at least 3.0 seconds
    }

    public static int getRequiredStareTicks(MicroPartyMinigame game) {
        if (game == null) return 20;
        float factor = game.getSpeedFactor();
        return Math.max(12, Math.round(20 * factor)); // ~1.0s scaled down to 0.6s
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.stareTicks.clear();
        this.passedPlayers.clear();

        // Solo safeguard
        if (game.getLivingPlayers().size() <= 1) {
            for (ServerPlayerEntity p : game.getLivingPlayers()) {
                this.passedPlayers.add(p.getUuid());
                p.sendMessage(Text.literal("§a✔ Solo survivor (Auto-pass)!"), true);
            }
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        int required = getRequiredStareTicks(game);
        ServerWorld world = game.getWorld();

        for (ServerPlayerEntity p1 : game.getLivingPlayers()) {
            if (this.passedPlayers.contains(p1.getUuid())) continue;

            Vec3d eye1 = p1.getEyePos();
            Vec3d look1 = p1.getRotationVec(1.0f).normalize();

            boolean staring = false;
            for (ServerPlayerEntity p2 : game.getLivingPlayers()) {
                if (p2.getUuid().equals(p1.getUuid())) continue;

                Vec3d eye2 = p2.getEyePos();
                Vec3d diff = eye2.subtract(eye1);
                double dist = diff.length();

                if (dist > 0.5 && dist <= 20.0) {
                    double dot = look1.dotProduct(diff.normalize());
                    if (dot > 0.96) { // Direct eye lock (~16 degree angle)
                        staring = true;
                        break;
                    }
                }
            }

            if (staring) {
                int count = this.stareTicks.merge(p1.getUuid(), 1, Integer::sum);
                if (count >= required) {
                    this.passedPlayers.add(p1.getUuid());
                    p1.sendMessage(Text.literal("§a§l✔ Stare Locked!"), true);
                    p1.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.6f);
                    if (world != null) {
                        world.spawnParticles(ParticleTypes.HEART, p1.getX(), p1.getEyeY() + 0.3, p1.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
                    }
                } else {
                    int pct = (count * 100) / required;
                    p1.sendMessage(Text.literal("§eStaring... §a" + pct + "%"), true);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        if (game != null && game.getLivingPlayers().size() <= 1) {
            return true;
        }
        return this.passedPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        this.stareTicks.clear();
        this.passedPlayers.clear();
    }
}
