package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class LookUpRule implements MicroRule {
    @Override
    public String id() {
        return "look_up";
    }

    @Override
    public String name() {
        return "Look Up";
    }

    @Override
    public String description() {
        return "Look straight up at the sky.";
    }

    @Override
    public Text title() {
        return Text.literal("LOOK UP!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Look straight up at the sky!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return player.getPitch() <= -70.0f;
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
    }
}
