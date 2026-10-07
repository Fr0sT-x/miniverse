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

public class JumpCountRule implements MicroRule {
    private final Map<UUID, Boolean> lastOnGround = new HashMap<>();
    private final java.util.Random random = new java.util.Random();
    private int requiredJumps = 3;

    @Override
    public String id() {
        return "jump_count";
    }

    @Override
    public String name() {
        return "Jump Count";
    }

    @Override
    public String description() {
        return "Jump exactly the required number of times (1-4). Doing more or less will fail!";
    }

    @Override
    public Text title() {
        return title(null);
    }

    @Override
    public Text title(MicroPartyMinigame game) {
        int req = (game != null && game.getActiveRule() instanceof JumpCountRule r) ? r.getRequiredJumps() : this.requiredJumps;
        return Text.literal("JUMP " + req + " TIME" + (req > 1 ? "S" : "") + "!").formatted(Formatting.GREEN, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return instruction(null);
    }

    @Override
    public Text instruction(MicroPartyMinigame game) {
        int req = (game != null && game.getActiveRule() instanceof JumpCountRule r) ? r.getRequiredJumps() : this.requiredJumps;
        return Text.literal("Jump EXACTLY " + req + " time" + (req > 1 ? "s" : "") + "! (No more, no less)").formatted(Formatting.YELLOW);
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
        int required = this.requiredJumps;
        player.sendMessage(Text.literal("§eJumps: §f0§7/§e" + required + " §7(Jump EXACTLY " + required + " time" + (required > 1 ? "s" : "") + "!)"), true);
    }

    public int getRequiredJumps() {
        return this.requiredJumps;
    }

    public void setRequiredJumps(int requiredJumps) {
        this.requiredJumps = Math.max(1, Math.min(4, requiredJumps));
    }

    public static int getRequiredJumps(MicroPartyMinigame game) {
        if (game != null && game.getActiveRule() instanceof JumpCountRule r) {
            return r.getRequiredJumps();
        }
        return 3;
    }

    @Override
    public void onPrepare(MicroPartyMinigame game, MinecraftServer server) {
        this.requiredJumps = 1 + random.nextInt(4); // Variable 1 to 4
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        lastOnGround.clear();
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            lastOnGround.put(p.getUuid(), p.isOnGround());
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        int required = this.requiredJumps;
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            boolean wasOnGround = lastOnGround.getOrDefault(p.getUuid(), true);
            boolean onGround = p.isOnGround();
            if (wasOnGround && !onGround && p.getVelocity().y > 0.08) {
                game.getTracker().incrementJump(p.getUuid());
                int count = game.getTracker().getJumpCount(p.getUuid());
                ServerWorld world = p.getServerWorld();
                if (count < required) {
                    p.sendMessage(Text.literal("§eJumps: §a" + count + "§7/§e" + required), true);
                    p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_SLIME_JUMP_SMALL, SoundCategory.PLAYERS, 0.6f, 1.0f + (count * 0.2f));
                } else if (count == required) {
                    p.sendMessage(Text.literal("§eJumps: §a" + count + "§7/§e" + required + " §a§l✔ (PERFECT! STOP!)"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.5f);
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                } else {
                    p.sendMessage(Text.literal("§c§l❌ TOO MANY JUMPS! (" + count + "/" + required + ")"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8f, 1.0f);
                    world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                }
            }
            lastOnGround.put(p.getUuid(), onGround);
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().getJumpCount(player.getUuid()) == this.requiredJumps;
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        lastOnGround.clear();
    }
}
