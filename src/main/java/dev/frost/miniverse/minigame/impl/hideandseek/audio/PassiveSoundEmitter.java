package dev.frost.miniverse.minigame.impl.hideandseek.audio;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Manages periodic 3D locational audio clues emitted by hiders:
 * 1. Broadcasts spatial audio cues (distinct chime/whistle) within a ~20-block radius.
 * 2. Displays action-bar countdowns and subtle tick alerts to hiders so they can prepare.
 * 3. Escalates frequency from 30s down to 15s during the final 90 seconds.
 */
public class PassiveSoundEmitter {
    private final ServerWorld world;
    private final int standardIntervalSeconds;
    private final int lateGameIntervalSeconds;
    private int countdownTicks;

    public PassiveSoundEmitter(ServerWorld world, int standardIntervalSeconds, int lateGameIntervalSeconds) {
        this.world = world;
        this.standardIntervalSeconds = Math.max(10, standardIntervalSeconds);
        this.lateGameIntervalSeconds = Math.max(5, lateGameIntervalSeconds);
        this.countdownTicks = this.standardIntervalSeconds * 20;
    }

    public void tick(int gameTicks, int remainingMatchSeconds, List<ServerPlayerEntity> aliveHiders, boolean inGracePeriod) {
        if (inGracePeriod || aliveHiders == null || aliveHiders.isEmpty()) {
            return;
        }

        int activeIntervalSeconds = (remainingMatchSeconds <= 90) ? this.lateGameIntervalSeconds : this.standardIntervalSeconds;
        int activeIntervalTicks = activeIntervalSeconds * 20;

        // If the interval dropped (e.g. entering late game), clamp remaining ticks
        if (this.countdownTicks > activeIntervalTicks) {
            this.countdownTicks = activeIntervalTicks;
        }

        this.countdownTicks--;

        if (this.countdownTicks <= 0) {
            emitSoundClue(aliveHiders);
            this.countdownTicks = activeIntervalTicks;
        } else if (this.countdownTicks % 20 == 0) {
            int secondsLeft = this.countdownTicks / 20;
            // Only alert hiders at 30s, 20s, 10s, and continuous 5..0s countdown
            if (secondsLeft == 30 || secondsLeft == 20 || secondsLeft == 10 || (secondsLeft <= 5 && secondsLeft >= 0)) {
                updateHiderActionBars(aliveHiders, secondsLeft);
            }
        }
    }

    private void emitSoundClue(List<ServerPlayerEntity> aliveHiders) {
        for (ServerPlayerEntity hider : aliveHiders) {
            if (hider == null || hider.isDisconnected() || hider.isSpectator()) {
                continue;
            }

            double x = hider.getX();
            double y = hider.getY();
            double z = hider.getZ();

            // Play distinct 3D spatial whistle / chime audible to nearby seekers (~40 blocks)
            this.world.playSound(null, x, y, z, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 2.5F, 1.4F);
            this.world.playSound(null, x, y, z, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 2.5F, 1.8F);

            // Visual feedback for the hider
            hider.sendMessage(Text.literal("🎵 CHIRP!").formatted(Formatting.RED, Formatting.BOLD), true);
        }
    }

    private void updateHiderActionBars(List<ServerPlayerEntity> aliveHiders, int secondsLeft) {
        Formatting color = (secondsLeft <= 3) ? Formatting.RED : (secondsLeft <= 5 ? Formatting.YELLOW : Formatting.GOLD);
        Text msg = Text.literal("Next Sound Clue: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.format("%02ds", secondsLeft)).formatted(color, Formatting.BOLD))
            .append(Text.literal(" [Stay alert!]").formatted(Formatting.DARK_GRAY));

        for (ServerPlayerEntity hider : aliveHiders) {
            if (hider == null || hider.isDisconnected() || hider.isSpectator()) {
                continue;
            }

            hider.sendMessage(msg, true);

            // Subtle countdown click cues for the last 3 seconds
            if (secondsLeft <= 3 && secondsLeft >= 1) {
                hider.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.4F, 1.2F + (3 - secondsLeft) * 0.3F);
            }
        }
    }

    public void clear() {
        this.countdownTicks = this.standardIntervalSeconds * 20;
    }
}
