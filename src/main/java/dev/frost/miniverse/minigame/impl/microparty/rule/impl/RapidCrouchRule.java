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
import java.util.UUID;

public class RapidCrouchRule implements MicroRule {
    private final Map<UUID, Boolean> lastSneakState = new HashMap<>();
    private final java.util.Random random = new java.util.Random();
    private int requiredCrouches = 3;

    @Override
    public String id() {
        return "rapid_crouch";
    }

    @Override
    public String name() {
        return "Rapid Crouch";
    }

    @Override
    public String description() {
        return "Crouch exactly the required number of times (1-4). Doing more or less will fail!";
    }

    @Override
    public Text title() {
        return title(null);
    }

    @Override
    public Text title(MicroPartyMinigame game) {
        int req = (game != null && game.getActiveRule() instanceof RapidCrouchRule r) ? r.getRequiredCrouches() : this.requiredCrouches;
        return Text.literal("CROUCH " + req + " TIME" + (req > 1 ? "S" : "") + "!").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return instruction(null);
    }

    @Override
    public Text instruction(MicroPartyMinigame game) {
        int req = (game != null && game.getActiveRule() instanceof RapidCrouchRule r) ? r.getRequiredCrouches() : this.requiredCrouches;
        return Text.literal("Crouch EXACTLY " + req + " time" + (req > 1 ? "s" : "") + "! (No more, no less)").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 5;
    }

    @Override
    public double minDurationSeconds() {
        return 3.0;
    }

    @Override
    public void sendInitialActionBar(MicroPartyMinigame game, ServerPlayerEntity player) {
        int required = this.requiredCrouches;
        player.sendMessage(Text.literal("§eCrouches: §f0§7/§e" + required + " §7(Crouch EXACTLY " + required + " time" + (required > 1 ? "s" : "") + "!)"), true);
    }

    public int getRequiredCrouches() {
        return this.requiredCrouches;
    }

    public void setRequiredCrouches(int requiredCrouches) {
        this.requiredCrouches = Math.max(1, Math.min(4, requiredCrouches));
    }

    public static int getRequiredCrouches(MicroPartyMinigame game) {
        if (game != null && game.getActiveRule() instanceof RapidCrouchRule r) {
            return r.getRequiredCrouches();
        }
        return 3;
    }

    @Override
    public void onPrepare(MicroPartyMinigame game, MinecraftServer server) {
        this.requiredCrouches = 1 + random.nextInt(4); // Variable 1 to 4
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        lastSneakState.clear();
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            lastSneakState.put(p.getUuid(), p.isSneaking());
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        int required = this.requiredCrouches;
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            boolean wasSneaking = lastSneakState.getOrDefault(p.getUuid(), false);
            boolean isSneaking = p.isSneaking();
            if (!wasSneaking && isSneaking) {
                game.getTracker().incrementSneak(p.getUuid());
                int count = game.getTracker().getSneakCount(p.getUuid());
                ServerWorld world = p.getServerWorld();
                if (count < required) {
                    p.sendMessage(Text.literal("§eCrouches: §a" + count + "§7/§e" + required), true);
                    p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.5f, 1.0f + (count * 0.15f));
                } else if (count == required) {
                    p.sendMessage(Text.literal("§eCrouches: §a" + count + "§7/§e" + required + " §a§l✔ (PERFECT! STOP!)"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.5f);
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                } else {
                    p.sendMessage(Text.literal("§c§l❌ TOO MANY CROUCHES! (" + count + "/" + required + ")"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8f, 1.0f);
                    world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                }
            }
            lastSneakState.put(p.getUuid(), isSneaking);
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().getSneakCount(player.getUuid()) == this.requiredCrouches;
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        lastSneakState.clear();
    }
}
