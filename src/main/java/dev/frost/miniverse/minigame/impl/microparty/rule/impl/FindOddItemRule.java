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

import java.util.*;

public class FindOddItemRule implements MicroRule {
    private final Random random = new Random();

    private final Map<UUID, Integer> oddSlots = new HashMap<>();

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
        return "Open your inventory (E) and click the unique item hidden among the filler items.";
    }

    @Override
    public Text title() {
        return Text.literal("FIND THE ODD ONE!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Open inventory (E) and click the unique item!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public double minDurationSeconds() {
        return 4.5;
    }

    @Override
    public void sendInitialActionBar(MicroPartyMinigame game, ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§eOpen inventory (E) and click the Blaze Rod!"), true);
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.oddSlots.clear();
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();

            // Hotbar (0-8) is kept completely empty
            // Main inventory slots are 9-35 (27 slots)
            int targetOddSlot = 9 + random.nextInt(27);
            this.oddSlots.put(p.getUuid(), targetOddSlot);

            for (int i = 9; i < 36; i++) {
                if (i == targetOddSlot) {
                    p.getInventory().setStack(i, new ItemStack(Items.BLAZE_ROD));
                } else {
                    p.getInventory().setStack(i, new ItemStack(Items.STICK));
                }
            }

            p.currentScreenHandler.sendContentUpdates();
            game.getTracker().setPassedCurrentRound(p.getUuid(), false);
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (!game.getTracker().hasPassedCurrentRound(p.getUuid())) {
                int targetSlot = this.oddSlots.getOrDefault(p.getUuid(), -1);

                // Clicking the item picks it up on cursor, moves it, or shifts it into hotbar
                boolean onCursor = p.currentScreenHandler.getCursorStack().isOf(Items.BLAZE_ROD);
                boolean movedFromSlot = targetSlot >= 0 && !p.getInventory().getStack(targetSlot).isOf(Items.BLAZE_ROD);

                if (onCursor || movedFromSlot) {
                    game.getTracker().setPassedCurrentRound(p.getUuid(), true);
                    p.sendMessage(Text.literal("§a§l✔ Found the odd item!"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.6f);
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().hasPassedCurrentRound(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        this.oddSlots.clear();
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
        }
    }
}
