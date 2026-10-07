package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

public class StatueRule implements MicroRule {
    private int ticksElapsed = 0;
    private boolean frozen = false;

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
        return "Freeze! Don't move a single muscle once the ice snaps.";
    }

    @Override
    public Text title() {
        return Text.literal("STATUE!").formatted(Formatting.AQUA, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Stop moving before you freeze!").formatted(Formatting.YELLOW);
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
    public void sendInitialActionBar(MicroPartyMinigame game, ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§e⚠️ FREEZE IN: §c1.2s §7(Slow down & stop!)"), true);
    }

    private int getTelegraphTicks(MicroPartyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        return Math.max(16, Math.round(25 * factor));
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.ticksElapsed = 0;
        this.frozen = false;
        // Position is NOT recorded yet - players have telegraph window to brake!
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        this.ticksElapsed++;
        int telegraphTicks = getTelegraphTicks(game);

        if (!this.frozen) {
            int remainingTelegraph = telegraphTicks - this.ticksElapsed;
            if (remainingTelegraph > 0) {
                // Countdown beeps and warning
                if (this.ticksElapsed % 5 == 0) {
                    float secs = Math.max(0.1f, remainingTelegraph / 20.0f);
                    for (ServerPlayerEntity p : game.getLivingPlayers()) {
                        p.sendMessage(Text.literal(String.format(Locale.ROOT, "§e⚠️ FREEZE IN: §c%.1fs §7(Release WASD!)", secs)), true);
                        p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.7f, 1.2f + (this.ticksElapsed * 0.03f));
                        ServerWorld world = p.getServerWorld();
                        world.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.3, 0.3, 0.3, 0.02);
                    }
                }
            } else {
                // FREEZE SNAP!
                this.frozen = true;
                for (ServerPlayerEntity p : game.getLivingPlayers()) {
                    game.getTracker().recordInitialPosition(p);
                    p.networkHandler.sendPacket(new TitleFadeS2CPacket(2, 40, 10));
                    p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("❄️ FROZEN! ❄️").formatted(Formatting.AQUA, Formatting.BOLD)));
                    p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("DO NOT MOVE!").formatted(Formatting.WHITE, Formatting.BOLD)));
                    p.sendMessage(Text.literal("§b§l❄️ FROZEN! §fHold completely still!"), true);
                    p.playSoundToPlayer(SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.4f);
                    p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.2, 0.3, 0.2, 0.05);
                }
            }
            return; // No penalty during braking phase
        }

        // Frozen phase: strictly check movement
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (!game.getTracker().hasMovedViolated(p.getUuid())) {
                game.getTracker().checkMovementViolation(p, 0.25);
                if (game.getTracker().hasMovedViolated(p.getUuid())) {
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                    world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.6f, 1.8f);
                    p.sendMessage(Text.literal("§c❌ You moved! (FAILED)"), true);
                } else if (remainingTicks % 10 == 0) {
                    p.sendMessage(Text.literal("§b§l❄️ FROZEN! §aHolding position..."), true);
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
        this.frozen = false;
        this.ticksElapsed = 0;
    }
}
