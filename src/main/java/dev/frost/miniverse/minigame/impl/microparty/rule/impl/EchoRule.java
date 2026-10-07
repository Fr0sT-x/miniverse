package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class EchoRule implements MicroRule {
    private static final String[] STANDARD_PHRASES = {
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

    private static final String[] SHORT_PHRASES = {
        "gg",
        "run",
        "jump",
        "speed",
        "go go",
        "mine",
        "craft",
        "win",
        "party",
        "boom"
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
        return 9;
    }

    @Override
    public double minDurationSeconds() {
        return 4.5;
    }

    @Override
    public void sendInitialActionBar(MicroPartyMinigame game, ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§eType in chat: §b\"" + this.targetPhrase + "\""), true);
    }

    @Override
    public void onPrepare(MicroPartyMinigame game, MinecraftServer server) {
        this.passedPlayers.clear();
        boolean isFast = game != null && game.getSpeedFactor() <= 0.6f;
        String[] pool = isFast ? SHORT_PHRASES : STANDARD_PHRASES;
        this.targetPhrase = pool[random.nextInt(pool.length)];
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        if (this.targetPhrase == null || this.targetPhrase.isBlank()) {
            this.targetPhrase = STANDARD_PHRASES[random.nextInt(STANDARD_PHRASES.length)];
        }
        if (game != null) {
            for (ServerPlayerEntity p : game.getLivingPlayers()) {
                sendInitialActionBar(game, p);
            }
        }
    }

    @Override
    public boolean onChatMessage(ServerPlayerEntity player, String message, MicroPartyMinigame game) {
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
        // Strip outer quotes if player typed with quotes (since instruction shows "...")
        if (attempt.startsWith("\"") && attempt.endsWith("\"") && attempt.length() >= 2) {
            attempt = attempt.substring(1, attempt.length() - 1).trim();
        } else if (attempt.startsWith("'") && attempt.endsWith("'") && attempt.length() >= 2) {
            attempt = attempt.substring(1, attempt.length() - 1).trim();
        }

        // Normalize internal whitespace
        String normalizedAttempt = attempt.replaceAll("\\s+", " ");
        String normalizedTarget = this.targetPhrase.replaceAll("\\s+", " ");

        boolean matches = normalizedAttempt.equalsIgnoreCase(normalizedTarget);
        if (!matches && normalizedAttempt.endsWith(".") && !normalizedTarget.endsWith(".")) {
            matches = normalizedAttempt.substring(0, normalizedAttempt.length() - 1).trim().equalsIgnoreCase(normalizedTarget);
        }
        if (!matches && normalizedAttempt.endsWith("!") && !normalizedTarget.endsWith("!")) {
            matches = normalizedAttempt.substring(0, normalizedAttempt.length() - 1).trim().equalsIgnoreCase(normalizedTarget);
        }

        if (matches) {
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
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return this.passedPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        this.passedPlayers.clear();
    }
}
