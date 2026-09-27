package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
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
        return "Crouch repeatedly as fast as you can.";
    }

    @Override
    public Text title() {
        return Text.literal("CROUCH RAPIDLY!").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return instruction(null);
    }

    @Override
    public Text instruction(MicroFrenzyMinigame game) {
        return Text.literal("Crouch " + getRequiredCrouches(game) + " times!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    public static int getRequiredCrouches(MicroFrenzyMinigame game) {
        if (game == null) {
            return 5;
        }
        float speed = game.getSpeedFactor();
        if (speed >= 0.9f) {
            return 5;
        } else if (speed >= 0.7f) {
            return 4;
        } else {
            return 3;
        }
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        lastSneakState.clear();
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            lastSneakState.put(p.getUuid(), p.isSneaking());
        }
    }

    @Override
    public void onTick(MicroFrenzyMinigame game, MinecraftServer server, int remainingTicks) {
        int required = getRequiredCrouches(game);
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            boolean wasSneaking = lastSneakState.getOrDefault(p.getUuid(), false);
            boolean isSneaking = p.isSneaking();
            if (!wasSneaking && isSneaking) {
                game.getTracker().incrementSneak(p.getUuid());
                int count = game.getTracker().getSneakCount(p.getUuid());
                if (count <= required) {
                    p.sendMessage(Text.literal("§eCrouches: §a" + count + "§7/§e" + required), true);
                    p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.5f, 1.0f + (count * 0.15f));
                    if (count == required) {
                        ServerWorld world = p.getServerWorld();
                        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.5f);
                    }
                }
            }
            lastSneakState.put(p.getUuid(), isSneaking);
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return game.getTracker().getSneakCount(player.getUuid()) >= getRequiredCrouches(game);
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        lastSneakState.clear();
    }
}
