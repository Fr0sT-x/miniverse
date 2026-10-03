package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
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

public class FindOddItemRule implements MicroRule {
    private final Random random = new Random();

    @Override
    public String id() {
        return "find_odd_item";
    }

    @Override
    public String name() {
        return "Find The Odd One";
    }

    @Override
    public String description() {
        return "Quickly spot the unique item in your hotbar and select it.";
    }

    @Override
    public Text title() {
        return Text.literal("FIND THE ODD ONE!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Switch to the unique item in your hotbar!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();

            int oddSlot = random.nextInt(9);
            for (int i = 0; i < 9; i++) {
                if (i == oddSlot) {
                    p.getInventory().setStack(i, new ItemStack(Items.BLAZE_ROD));
                } else {
                    p.getInventory().setStack(i, new ItemStack(Items.STICK));
                }
            }

            // Ensure currently active slot is NOT the odd slot
            p.getInventory().selectedSlot = (oddSlot + 4) % 9;
            p.currentScreenHandler.sendContentUpdates();

            game.getTracker().setPassedCurrentRound(p.getUuid(), false);
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (!game.getTracker().hasPassedCurrentRound(p.getUuid())) {
                if (p.getMainHandStack().isOf(Items.BLAZE_ROD)) {
                    game.getTracker().setPassedCurrentRound(p.getUuid(), true);
                    p.sendMessage(Text.literal("§a§l✔ Found it!"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.6f);
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return player.getMainHandStack().isOf(Items.BLAZE_ROD);
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
        }
    }
}
