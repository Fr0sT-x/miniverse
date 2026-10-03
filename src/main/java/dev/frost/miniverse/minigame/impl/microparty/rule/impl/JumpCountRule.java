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
        return "Jump repeatedly to reach the required count.";
    }

    @Override
    public Text title() {
        return Text.literal("JUMP! JUMP!").formatted(Formatting.GREEN, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return instruction(null);
    }

    @Override
    public Text instruction(MicroPartyMinigame game) {
        return Text.literal("Jump " + getRequiredJumps(game) + " times!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    public static int getRequiredJumps(MicroPartyMinigame game) {
        if (game == null) {
            return 4;
        }
        float speed = game.getSpeedFactor();
        if (speed >= 0.9f) {
            return 4;
        } else if (speed >= 0.7f) {
            return 3;
        } else {
            return 2;
        }
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
        int required = getRequiredJumps(game);
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            boolean wasOnGround = lastOnGround.getOrDefault(p.getUuid(), true);
            boolean onGround = p.isOnGround();
            if (wasOnGround && !onGround && p.getVelocity().y > 0.08) {
                game.getTracker().incrementJump(p.getUuid());
                int count = game.getTracker().getJumpCount(p.getUuid());
                if (count <= required) {
                    p.sendMessage(Text.literal("§eJumps: §a" + count + "§7/§e" + required), true);
                    p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_SLIME_JUMP_SMALL, SoundCategory.PLAYERS, 0.6f, 1.0f + (count * 0.2f));
                    if (count == required) {
                        ServerWorld world = p.getServerWorld();
                        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.5f);
                    }
                }
            }
            lastOnGround.put(p.getUuid(), onGround);
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().getJumpCount(player.getUuid()) >= getRequiredJumps(game);
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        lastOnGround.clear();
    }
}
