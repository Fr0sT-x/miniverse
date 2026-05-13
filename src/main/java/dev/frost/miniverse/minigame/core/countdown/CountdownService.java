package dev.frost.miniverse.minigame.core.countdown;

import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class CountdownService {
    private final Set<Integer> announcedSeconds = new HashSet<>();

    public void reset() {
        this.announcedSeconds.clear();
    }

    public boolean announceOnce(Collection<ServerPlayerEntity> players, int secondsRemaining, Text actionbarText) {
        if (secondsRemaining < 0 || !this.announcedSeconds.add(secondsRemaining)) {
            return false;
        }
        players.forEach(player -> player.sendMessage(actionbarText, true));
        return true;
    }

    public boolean announceVisibleCountdown(
        Collection<ServerPlayerEntity> players,
        int secondsRemaining,
        int visibleAtSeconds,
        Text title,
        @Nullable SoundEvent sound
    ) {
        if (secondsRemaining <= 0 || secondsRemaining > visibleAtSeconds || !this.announcedSeconds.add(secondsRemaining)) {
            return false;
        }

        Text subtitle = Text.literal(Integer.toString(secondsRemaining)).formatted(Formatting.YELLOW, Formatting.BOLD);
        for (ServerPlayerEntity player : players) {
            player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
            player.networkHandler.sendPacket(new TitleS2CPacket(title.copy().formatted(Formatting.GOLD, Formatting.BOLD)));
            player.sendMessage(Text.literal(secondsRemaining + "s").formatted(Formatting.YELLOW), true);
            if (sound != null) {
                player.playSound(sound, 1.0F, 1.0F);
            }
        }
        return true;
    }
}
