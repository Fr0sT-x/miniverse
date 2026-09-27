package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Random;

public class EatFoodRule implements MicroRule {
    private final Random random = new Random();

    @Override
    public String id() {
        return "eat_food";
    }

    @Override
    public String name() {
        return "Feast";
    }

    @Override
    public String description() {
        return "Find the food in your hotbar and eat it before time runs out.";
    }

    @Override
    public Text title() {
        return Text.literal("FEAST!").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Find the food in your hotbar and eat it!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            p.getHungerManager().setFoodLevel(12); // Lower hunger to permit eating immediately

            // Place food in any randomized hotbar slot 1-9 (index 0-8)
            int targetSlot = random.nextInt(9);
            p.getInventory().setStack(targetSlot, new ItemStack(Items.APPLE));

            // Select a different slot so player has to switch
            p.getInventory().selectedSlot = (targetSlot + 4) % 9;
            p.currentScreenHandler.sendContentUpdates();

            game.getTracker().setPassedCurrentRound(p.getUuid(), false);
        }
    }

    @Override
    public void onTick(MicroFrenzyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (!game.getTracker().hasPassedCurrentRound(p.getUuid())) {
                // If the player consumed the apple (food level rose above 12 and inventory no longer has it)
                if (p.getHungerManager().getFoodLevel() > 12) {
                    game.getTracker().setPassedCurrentRound(p.getUuid(), true);
                    p.sendMessage(Text.literal("§a§l✔ Delicious!"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return game.getTracker().hasPassedCurrentRound(player.getUuid());
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            p.getHungerManager().setFoodLevel(20);
        }
    }
}
