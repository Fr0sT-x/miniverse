package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class PunchFriendRule implements MicroRule {
    @Override
    public String id() {
        return "punch_friend";
    }

    @Override
    public String name() {
        return "Slap Fest";
    }

    @Override
    public String description() {
        return "Punch another player in the arena.";
    }

    @Override
    public Text title() {
        return Text.literal("SLAP FEST!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Punch another player!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public int getDurationTicks(MicroFrenzyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(8 * 20 * factor);
        return Math.max(80, standardTicks); // Clamped to at least 4.0 seconds (80 ticks)
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
    }

    @Override
    public void onPlayerAttack(ServerPlayerEntity attacker, Entity target, MicroFrenzyMinigame game) {
        if (target instanceof ServerPlayerEntity victim && !victim.getUuid().equals(attacker.getUuid())) {
            if (!game.getTracker().hasPassedCurrentRound(attacker.getUuid())) {
                game.getTracker().setPassedCurrentRound(attacker.getUuid(), true);
                ServerWorld world = attacker.getServerWorld();
                world.spawnParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + 1.0, victim.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                world.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.8f, 1.2f);
                attacker.sendMessage(Text.literal("§a✔ Nice hit!"), true);
            }
        }
    }

    @Override
    public boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount, MicroFrenzyMinigame game) {
        // Cancel actual health damage — the slap is what counts
        return false;
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return game.getTracker().hasPassedCurrentRound(player.getUuid());
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
    }
}
