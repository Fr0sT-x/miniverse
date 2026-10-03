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
import java.util.Map;
import java.util.UUID;

public class KeepMovingRule implements MicroRule {
    private final Map<UUID, Vec3d> lastPositions = new HashMap<>();
    private final Map<UUID, Integer> stationaryTicks = new HashMap<>();

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
        return "Keep sprinting! If you stand still for even a moment, you fail.";
    }

    @Override
    public Text title() {
        return Text.literal("DON'T STOP!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Keep running! Do NOT stop moving!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.lastPositions.clear();
        this.stationaryTicks.clear();

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            this.lastPositions.put(p.getUuid(), p.getPos());
            this.stationaryTicks.put(p.getUuid(), 0);
            game.getTracker().setPassedCurrentRound(p.getUuid(), true); // Pass unless they stop
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            UUID id = p.getUuid();
            Vec3d last = this.lastPositions.getOrDefault(id, p.getPos());
            double dist = Math.hypot(p.getX() - last.x, p.getZ() - last.z);

            if (dist < 0.05) {
                int stopped = this.stationaryTicks.merge(id, 1, Integer::sum);
                if (stopped >= 10 && game.getTracker().hasPassedCurrentRound(id)) {
                    game.getTracker().setPassedCurrentRound(id, false);
                    p.sendMessage(Text.literal("§c❌ You stopped moving!"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.9f, 1.0f);
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                }
            } else {
                this.stationaryTicks.put(id, 0);
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
    }
}
