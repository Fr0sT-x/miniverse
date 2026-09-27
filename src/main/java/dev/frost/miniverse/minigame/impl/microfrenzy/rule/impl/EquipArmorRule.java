package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.entity.EquipmentSlot;
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

public class EquipArmorRule implements MicroRule {
    private final Random random = new Random();

    @Override
    public String id() {
        return "equip_armor";
    }

    @Override
    public String name() {
        return "Gear Up";
    }

    @Override
    public String description() {
        return "Find the helmet in your hotbar and equip it onto your head.";
    }

    @Override
    public Text title() {
        return Text.literal("GEAR UP!").formatted(Formatting.AQUA, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Put on your helmet immediately!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);

            // Place helmet in any randomized hotbar slot 1-9 (index 0-8)
            int targetSlot = random.nextInt(9);
            p.getInventory().setStack(targetSlot, new ItemStack(Items.IRON_HELMET));

            p.getInventory().selectedSlot = (targetSlot + 4) % 9;
            p.currentScreenHandler.sendContentUpdates();

            game.getTracker().setPassedCurrentRound(p.getUuid(), false);
        }
    }

    @Override
    public void onTick(MicroFrenzyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (!game.getTracker().hasPassedCurrentRound(p.getUuid())) {
                if (p.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.IRON_HELMET)) {
                    game.getTracker().setPassedCurrentRound(p.getUuid(), true);
                    p.sendMessage(Text.literal("§a§l✔ Geared Up!"), true);
                    p.playSoundToPlayer(SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), SoundCategory.PLAYERS, 1.0f, 1.2f);
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.8, p.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.IRON_HELMET);
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }
}
