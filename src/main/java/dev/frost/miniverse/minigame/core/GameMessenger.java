package dev.frost.miniverse.minigame.core;

import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

public final class GameMessenger {
    private GameMessenger() {
    }

    public static void broadcast(Collection<ServerPlayerEntity> players, Text message) {
        for (ServerPlayerEntity player : players) {
            player.sendMessage(message);
        }
    }

    public static void showGameTitle(Collection<ServerPlayerEntity> players, Text title, Text subtitle) {
        for (ServerPlayerEntity player : players) {
            player.networkHandler.sendPacket(new TitleS2CPacket(title));
            if (subtitle != null && !subtitle.getString().isEmpty()) {
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(subtitle));
            }
        }
    }

    public static void showGameOverTitle(Collection<ServerPlayerEntity> players, Text title) {
        for (ServerPlayerEntity player : players) {
            player.networkHandler.sendPacket(new TitleS2CPacket(title));
        }
    }
}
