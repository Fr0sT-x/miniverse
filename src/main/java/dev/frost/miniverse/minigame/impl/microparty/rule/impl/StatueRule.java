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

public class StatueRule implements MicroRule {
    @Override
    public String id() {
        return "statue";
    }

    @Override
    public String name() {
        return "Statue";
    }

    @Override
    public String description() {
        return "Freeze! Don't move a single muscle until time runs out.";
    }

    @Override
    public Text title() {
        return Text.literal("STATUE!").formatted(Formatting.AQUA, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Don't move a single muscle!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            game.getTracker().recordInitialPosition(p);
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (!game.getTracker().hasMovedViolated(p.getUuid())) {
                game.getTracker().checkMovementViolation(p, 0.25);
                if (game.getTracker().hasMovedViolated(p.getUuid())) {
                    // Strike with visual lightning/sparks
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                    world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.6f, 1.8f);
                    p.sendMessage(Text.literal("§c❌ You moved!"), true);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return !game.getTracker().hasMovedViolated(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
    }
}
