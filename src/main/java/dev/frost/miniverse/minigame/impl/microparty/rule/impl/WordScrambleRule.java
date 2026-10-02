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

public class WordScrambleRule implements MicroRule {
    private static final String[] WORD_LIST = {
        "DIAMOND", "EMERALD", "CREEPER", "OBSIDIAN", "NETHERITE",
        "REDSTONE", "SKELETON", "VILLAGER", "FIREWORK", "CAMPFIRE",
        "MINECART", "BEDROCK", "AMETHYST", "TRIDENT", "ELYTRA"
    };

    private final Set<UUID> passedPlayers = new HashSet<>();
    private final Random random = new Random();
    private String originalWord = "DIAMOND";
    private String scrambledWord = "DNMAOID";

    @Override
    public String id() {
        return "word_scramble";
    }

    @Override
    public String name() {
        return "Word Scramble";
    }

    @Override
    public String description() {
        return "Unscramble the scrambled Minecraft word and type it in chat!";
    }

    @Override
    public Text title() {
        return Text.literal("UNSCRAMBLE: " + this.scrambledWord).formatted(Formatting.AQUA, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Type the word in chat!").formatted(Formatting.YELLOW);
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

    private String scramble(String word) {
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) {
            chars.add(c);
        }
        for (int attempts = 0; attempts < 10; attempts++) {
            Collections.shuffle(chars, random);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) {
                sb.append(c);
            }
            if (!sb.toString().equalsIgnoreCase(word)) {
                return sb.toString();
            }
        }
        return chars.get(1) + word.substring(2) + chars.get(0);
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        this.passedPlayers.clear();
        this.originalWord = WORD_LIST[random.nextInt(WORD_LIST.length)];
        this.scrambledWord = scramble(this.originalWord);
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

        if (attempt.equalsIgnoreCase(this.originalWord)) {
            this.passedPlayers.add(player.getUuid());
            player.sendMessage(Text.literal("§a§l✔ Correct! (" + this.originalWord + ")"), true);
            player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
            return true; // SILENT SUPPRESSION on correct
        } else {
            player.sendMessage(Text.literal("§c§l✖ Wrong word!"), true);
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
