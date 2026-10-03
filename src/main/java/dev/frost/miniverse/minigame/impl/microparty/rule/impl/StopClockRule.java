package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class StopClockRule implements MicroRule {
    private static StopClockRule activeInstance;
    private static boolean receiverRegistered = false;

    private final Set<UUID> passedPlayers = new HashSet<>();
    private final Set<UUID> failedPlayers = new HashSet<>();
    private final Set<UUID> submittedPlayers = new HashSet<>();
    private long startServerTimeMs = 0;
    private int durationMs = 5000;

    @Override
    public String id() {
        return "stop_clock";
    }

    @Override
    public String name() {
        return "Stop the Clock";
    }

    @Override
    public String description() {
        return "Crouch exactly when the countdown timer hits 2.00s!";
    }

    @Override
    public Text title() {
        return Text.literal("STOP THE CLOCK!").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Crouch when timer hits 2.00s!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 5;
    }

    @Override
    public int getDurationTicks(MicroPartyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(5 * 20 * factor);
        return Math.max(60, standardTicks); // Minimum 3.0 seconds (60 ticks)
    }

    public static synchronized void ensureServerReceiverRegistered() {
        if (receiverRegistered) return;
        try {
            ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.STOP_CLOCK_STOP_ID, (payload, context) -> {
                ServerPlayerEntity player = context.player();
                int elapsed = payload.stoppedElapsedMs();
                context.server().execute(() -> {
                    if (activeInstance != null) {
                        activeInstance.handleClientStop(player, elapsed);
                    }
                });
            });
            receiverRegistered = true;
        } catch (Throwable ignored) {
            // Environment without active networking registry (e.g. unit tests)
        }
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        activeInstance = this;
        ensureServerReceiverRegistered();

        this.passedPlayers.clear();
        this.failedPlayers.clear();
        this.submittedPlayers.clear();

        this.startServerTimeMs = System.currentTimeMillis();
        int totalTicks = getDurationTicks(game);
        this.durationMs = totalTicks * 50;

        // Send start packet with millisecond info to client for smooth 60+ FPS client timer
        NetworkConstants.StopClockStartPayload payload = new NetworkConstants.StopClockStartPayload(this.durationMs, 2000, 350);
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            try {
                ServerPlayNetworking.send(p, payload);
            } catch (Throwable ignored) {
            }
        }
    }

    public void handleClientStop(ServerPlayerEntity player, int clientElapsedMs) {
        if (!this.submittedPlayers.add(player.getUuid())) {
            return; // Already submitted
        }

        int remainingMs = Math.max(0, this.durationMs - clientElapsedMs);
        float remainingSecs = remainingMs / 1000.0f;
        int diff = Math.abs(remainingMs - 2000);

        if (diff <= 350) {
            this.passedPlayers.add(player.getUuid());
            player.sendMessage(Text.literal(String.format(Locale.ROOT, "§a§l✔ Stopped at %.2fs (PERFECT!)", remainingSecs)), true);
            player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.6f);
        } else {
            this.failedPlayers.add(player.getUuid());
            player.sendMessage(Text.literal(String.format(Locale.ROOT, "§c§l✖ Stopped at %.2fs (MISSED!)", remainingSecs)), true);
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0f, 0.6f);
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        float remainingSecs = (remainingTicks * 50) / 1000.0f;

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            // Actionbar broadcast for vanilla / fallback
            if (!this.submittedPlayers.contains(p.getUuid())) {
                p.networkHandler.sendPacket(new OverlayMessageS2CPacket(
                    Text.literal(String.format(Locale.ROOT, "§e⏱ %.2fs §7| Crouch at §a2.00s", remainingSecs))
                ));

                // Fallback server detection if client didn't send custom stop packet
                if (p.isSneaking()) {
                    long now = System.currentTimeMillis();
                    int elapsed = (int) (now - this.startServerTimeMs);
                    handleClientStop(p, elapsed);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return this.passedPlayers.contains(player.getUuid()) && !this.failedPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        if (activeInstance == this) {
            activeInstance = null;
        }
        this.passedPlayers.clear();
        this.failedPlayers.clear();
        this.submittedPlayers.clear();
    }
}
