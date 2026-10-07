package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Random;

public class QuickMathRule implements MicroRule {
    private final Random random = new Random();
    private String currentQuestion = "5 + 5";
    private int expectedAnswer = 10;

    @Override
    public String id() {
        return "quick_math";
    }

    @Override
    public String name() {
        return "Quick Math";
    }

    @Override
    public String description() {
        return "Solve the math equation and type the answer in chat.";
    }

    @Override
    public Text title() {
        return Text.literal("SOLVE IT!").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("What is " + currentQuestion + "? Type in chat!").formatted(Formatting.YELLOW);
    }

    @Override
    public Text instruction(MicroPartyMinigame game) {
        return Text.literal("What is " + currentQuestion + "? Type in chat!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 9;
    }

    @Override
    public double minDurationSeconds() {
        return 5.0;
    }

    @Override
    public void sendInitialActionBar(MicroPartyMinigame game, ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§eMath: §b" + this.currentQuestion + " = ?"), true);
    }

    public void generateNewQuestion() {
        generateNewQuestion(null);
    }

    public void generateNewQuestion(MicroPartyMinigame game) {
        boolean fast = game != null && game.getSpeedFactor() <= 0.6f;
        if (fast) {
            int op = random.nextInt(2);
            if (op == 0) {
                int a = random.nextInt(9) + 1;
                int b = random.nextInt(9) + 1;
                this.currentQuestion = a + " + " + b;
                this.expectedAnswer = a + b;
            } else {
                int a = random.nextInt(9) + 5;
                int b = random.nextInt(a - 1) + 1;
                this.currentQuestion = a + " - " + b;
                this.expectedAnswer = a - b;
            }
        } else {
            int op = random.nextInt(3);
            if (op == 0) { // Addition
                int a = random.nextInt(15) + 3;
                int b = random.nextInt(15) + 2;
                this.currentQuestion = a + " + " + b;
                this.expectedAnswer = a + b;
            } else if (op == 1) { // Subtraction
                int a = random.nextInt(20) + 10;
                int b = random.nextInt(a - 2) + 2;
                this.currentQuestion = a + " - " + b;
                this.expectedAnswer = a - b;
            } else { // Multiplication
                int a = random.nextInt(7) + 2;
                int b = random.nextInt(7) + 2;
                this.currentQuestion = a + " × " + b;
                this.expectedAnswer = a * b;
            }
        }
    }

    public int getExpectedAnswer() {
        return this.expectedAnswer;
    }

    public String getCurrentQuestion() {
        return this.currentQuestion;
    }

    @Override
    public void onPrepare(MicroPartyMinigame game, MinecraftServer server) {
        generateNewQuestion(game);
        if (game != null) {
            for (ServerPlayerEntity p : game.getLivingPlayers()) {
                game.getTracker().setPassedCurrentRound(p.getUuid(), false);
            }
        }
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        if (this.currentQuestion == null || this.currentQuestion.isBlank()) {
            generateNewQuestion(game);
        }
        if (game != null) {
            for (ServerPlayerEntity p : game.getLivingPlayers()) {
                sendInitialActionBar(game, p);
            }
        }
    }

    @Override
    public boolean onChatMessage(ServerPlayerEntity player, String message, MicroPartyMinigame game) {
        if (message == null || game.isEliminated(player.getUuid()) || !game.getTracker().isAlive(player.getUuid())) {
            return false;
        }

        String trimmed = message.trim();
        if (trimmed.startsWith("!")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.startsWith("=") || trimmed.startsWith("?")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.equals(String.valueOf(this.expectedAnswer))) {
            if (!game.getTracker().hasPassedCurrentRound(player.getUuid())) {
                game.getTracker().setPassedCurrentRound(player.getUuid(), true);
                player.sendMessage(Text.literal("§a§l✔ Correct! (" + this.expectedAnswer + ")"), true);
                player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                player.getServerWorld().spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.25, 0.25, 0.25, 0.05);
            }
            return true; // Silent interception! Chat message is not shown
        }

        // Wrong answer: play buzz, but return false so message is shown in public chat!
        player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8f, 1.0f);
        player.sendMessage(Text.literal("§c❌ Incorrect answer!"), true);
        return false;
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().hasPassedCurrentRound(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
    }
}
