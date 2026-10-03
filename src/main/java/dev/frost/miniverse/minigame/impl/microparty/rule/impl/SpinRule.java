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

public class SpinRule implements MicroRule {
    @Override
    public String id() {
        return "spin";
    }

    @Override
    public String name() {
        return "Spin Around";
    }

    @Override
    public String description() {
        return "Spin your camera quickly to perform a rotation.";
    }

    @Override
    public Text title() {
        return Text.literal("SPIN AROUND!").formatted(Formatting.AQUA, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return instruction(null);
    }

    @Override
    public Text instruction(MicroPartyMinigame game) {
        float rot = getRequiredRotation(game);
        if (rot >= 300.0f) {
            return Text.literal("Do a full 360° spin!").formatted(Formatting.YELLOW);
        } else {
            return Text.literal("Do a quick 180° spin!").formatted(Formatting.YELLOW);
        }
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    public static float getRequiredRotation(MicroPartyMinigame game) {
        if (game == null || game.getSpeedFactor() >= 0.7f) {
            return 340.0f;
        }
        return 200.0f;
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            game.getTracker().trackYawRotation(p);
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        float required = getRequiredRotation(game);
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (!hasPassed(p, game)) {
                game.getTracker().trackYawRotation(p);
                float rot = game.getTracker().getCumulativeYawDelta(p.getUuid());
                if (rot >= required) {
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                    world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.5f);
                    p.sendMessage(Text.literal("§a✔ Spin complete!"), true);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().getCumulativeYawDelta(player.getUuid()) >= getRequiredRotation(game);
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
    }
}
