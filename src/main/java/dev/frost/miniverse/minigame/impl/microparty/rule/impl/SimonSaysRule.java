package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class SimonSaysRule implements MicroRule {
    private final Random random = new Random();
    private boolean isSimonSays = true;
    private int actionType = 0; // 0 = Crouch, 1 = Jump, 2 = Look Up
    private final Map<UUID, Boolean> lastOnGround = new HashMap<>();

    @Override
    public String id() {
        return "simon_says";
    }

    @Override
    public String name() {
        return "Simon Says";
    }

    @Override
    public String description() {
        return "Follow the instruction ONLY if Simon says so!";
    }

    @Override
    public Text title() {
        String action = switch (actionType) {
            case 0 -> "CROUCH!";
            case 1 -> "JUMP!";
            default -> "LOOK UP!";
        };
        if (isSimonSays) {
            return Text.literal("SIMON SAYS: " + action).formatted(Formatting.GREEN, Formatting.BOLD);
        } else {
            return Text.literal(action).formatted(Formatting.RED, Formatting.BOLD);
        }
    }

    @Override
    public Text instruction() {
        if (isSimonSays) {
            return Text.literal("Simon says: Do the action!").formatted(Formatting.YELLOW);
        } else {
            return Text.literal("Simon DIDN'T say! Do NOT do it!").formatted(Formatting.GOLD);
        }
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public void onPrepare(MicroPartyMinigame game, MinecraftServer server) {
        this.lastOnGround.clear();
        this.isSimonSays = random.nextBoolean();
        this.actionType = random.nextInt(3);
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            this.lastOnGround.put(p.getUuid(), p.isOnGround());
            // If Simon said so, you must do it (starts as false)
            // If Simon did NOT say so, you must avoid it (starts as true)
            game.getTracker().setPassedCurrentRound(p.getUuid(), !this.isSimonSays);
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            boolean didAction = false;
            if (this.actionType == 0) { // Crouch
                didAction = p.isSneaking();
            } else if (this.actionType == 1) { // Jump
                boolean wasGround = this.lastOnGround.getOrDefault(p.getUuid(), true);
                didAction = wasGround && !p.isOnGround() && p.getVelocity().y > 0.08;
            } else if (this.actionType == 2) { // Look Up
                didAction = p.getPitch() <= -65.0f;
            }

            if (didAction) {
                if (this.isSimonSays) {
                    if (!game.getTracker().hasPassedCurrentRound(p.getUuid())) {
                        game.getTracker().setPassedCurrentRound(p.getUuid(), true);
                        p.sendMessage(Text.literal("§a§l✔ Simon is pleased!"), true);
                        p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
                    }
                } else {
                    if (game.getTracker().hasPassedCurrentRound(p.getUuid())) {
                        game.getTracker().setPassedCurrentRound(p.getUuid(), false);
                        p.sendMessage(Text.literal("§c❌ Simon didn't say!"), true);
                        p.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.9f, 1.0f);
                        ServerWorld world = p.getServerWorld();
                        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            }

            this.lastOnGround.put(p.getUuid(), p.isOnGround());
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().hasPassedCurrentRound(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        this.lastOnGround.clear();
    }
}
