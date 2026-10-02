package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReversePsychologyRule implements MicroRule {
    private final Map<UUID, Boolean> lastOnGround = new HashMap<>();

    @Override
    public String id() {
        return "do_not_jump";
    }

    @Override
    public String name() {
        return "Do Not Jump";
    }

    @Override
    public String description() {
        return "Reverse psychology: whatever you do, DO NOT jump!";
    }

    @Override
    public Text title() {
        return Text.literal("DO NOT JUMP!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Whatever you do, DO NOT JUMP!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        lastOnGround.clear();
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            lastOnGround.put(p.getUuid(), p.isOnGround());
        }
    }

    @Override
    public void onTick(MicroFrenzyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            boolean wasOnGround = lastOnGround.getOrDefault(p.getUuid(), true);
            boolean onGround = p.isOnGround();
            if (wasOnGround && !onGround && p.getVelocity().y > 0.08) {
                game.getTracker().incrementJump(p.getUuid());
                ServerWorld world = p.getServerWorld();
                world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8f, 1.0f);
                p.sendMessage(Text.literal("§c❌ You jumped!"), true);
            }
            lastOnGround.put(p.getUuid(), onGround);
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return game.getTracker().getJumpCount(player.getUuid()) == 0;
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        lastOnGround.clear();
    }
}
