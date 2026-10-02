package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class EchoRule implements MicroRule {
    private static final String[] PHRASE_LIST = {
        "miniverse rocks",
        "never dig down",
        "praise the sun",
        "creeper aw man",
        "diamonds are forever",
        "crafting table",
        "speedrunner",
        "punch the wood",
        "eat golden apple",
        "dont touch that"
    };

    private final Set<UUID> passedPlayers = new HashSet<>();
    private final Random random = new Random();
    private String targetPhrase = "miniverse rocks";

    @Override
    public String id() {
        return "echo_phrase";
    }

    @Override
    public String name() {
        return "Echo";
    }

    @Override
    public String description() {
        return "Type the exact phrase shown on screen into chat!";
    }

    @Override
    public Text title() {
        return Text.literal("REPEAT THE PHRASE!").formatted(Formatting.GREEN, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Type: \"" + this.targetPhrase + "\"").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public int getDurationTicks(MicroFrenzyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(8 * 20 * factor);
        return Math.max(70, standardTicks); // Clamped to at least 3.5 seconds
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        this.passedPlayers.clear();
        this.targetPhrase = PHRASE_LIST[random.nextInt(PHRASE_LIST.length)];
    }

    @Override
    public boolean onChatMessage(ServerPlayerEntity player, String message, MicroFrenzyMinigame game) {
        if (player == null || message == null || game.isEliminated(player.getUuid()) || !game.getTracker().isAlive(player.getUuid())) {
            return false;
        }

        if (this.passedPlayers.contains(player.getUuid())) {
            return true; // Already passed, consume chat quietly
        }

        String attempt = message.trim();
        if (attempt.startsWith("!")) {
            attempt = attempt.substring(1).trim();
        }

        if (attempt.equalsIgnoreCase(this.targetPhrase)) {
            this.passedPlayers.add(player.getUuid());
            player.sendMessage(Text.literal("§a§l✔ Echoed!"), true);
            player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
            return true; // SILENT SUPPRESSION on correct
        } else {
            player.sendMessage(Text.literal("§c§l✖ Typo! Try again!"), true);
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0f, 0.6f);
            return false; // PUBLIC BROADCAST on wrong
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return this.passedPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        this.passedPlayers.clear();
    }
}
