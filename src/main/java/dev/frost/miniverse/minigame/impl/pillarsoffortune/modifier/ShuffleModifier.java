package dev.frost.miniverse.minigame.impl.pillarsoffortune.modifier;

import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.countdown.CountdownService;
import dev.frost.miniverse.minigame.impl.pillarsoffortune.PillarsOfFortuneMinigame;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShuffleModifier implements GameModifier {
    public static final String ID = "shuffle";
    private final CountdownService countdownService = new CountdownService();
    private int ticksUntilShuffle = 1200;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void tick(PillarsOfFortuneMinigame minigame) {
        if (this.ticksUntilShuffle > 0) {
            this.ticksUntilShuffle--;
            int secondsRemaining = this.ticksUntilShuffle / 20;
            if (this.ticksUntilShuffle % 20 == 0 && secondsRemaining <= 5 && secondsRemaining > 0) {
                this.countdownService.announceVisibleCountdown(
                    minigame.getAliveParticipants(),
                    secondsRemaining,
                    5,
                    Text.literal("Shuffle!").formatted(Formatting.LIGHT_PURPLE),
                    Text.literal("Inventory shuffle in " + secondsRemaining + "s!").formatted(Formatting.YELLOW),
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()
                );
            }
            if (this.ticksUntilShuffle == 0) {
                this.shuffleInventories(minigame);
                this.ticksUntilShuffle = 1200;
                this.countdownService.reset();
            }
        }
    }

    private void shuffleInventories(PillarsOfFortuneMinigame minigame) {
        List<ServerPlayerEntity> alivePlayers = new ArrayList<>(minigame.getAliveParticipants());
        if (alivePlayers.size() < 2) {
            return;
        }

        List<DefaultedList<ItemStack>> mainInventories = new ArrayList<>();
        List<DefaultedList<ItemStack>> armors = new ArrayList<>();
        List<DefaultedList<ItemStack>> offHands = new ArrayList<>();

        for (ServerPlayerEntity player : alivePlayers) {
            DefaultedList<ItemStack> mainInvClone = DefaultedList.ofSize(player.getInventory().main.size(), ItemStack.EMPTY);
            for (int i = 0; i < player.getInventory().main.size(); i++) {
                mainInvClone.set(i, player.getInventory().main.get(i).copy());
            }
            mainInventories.add(mainInvClone);

            DefaultedList<ItemStack> armorClone = DefaultedList.ofSize(player.getInventory().armor.size(), ItemStack.EMPTY);
            for (int i = 0; i < player.getInventory().armor.size(); i++) {
                armorClone.set(i, player.getInventory().armor.get(i).copy());
            }
            armors.add(armorClone);

            DefaultedList<ItemStack> offHandClone = DefaultedList.ofSize(player.getInventory().offHand.size(), ItemStack.EMPTY);
            for (int i = 0; i < player.getInventory().offHand.size(); i++) {
                offHandClone.set(i, player.getInventory().offHand.get(i).copy());
            }
            offHands.add(offHandClone);
        }

        Collections.shuffle(mainInventories);
        Collections.shuffle(armors);
        Collections.shuffle(offHands);

        for (int i = 0; i < alivePlayers.size(); i++) {
            ServerPlayerEntity player = alivePlayers.get(i);
            DefaultedList<ItemStack> newMain = mainInventories.get(i);
            DefaultedList<ItemStack> newArmor = armors.get(i);
            DefaultedList<ItemStack> newOffHand = offHands.get(i);

            for (int j = 0; j < newMain.size(); j++) {
                player.getInventory().main.set(j, newMain.get(j));
            }
            for (int j = 0; j < newArmor.size(); j++) {
                player.getInventory().armor.set(j, newArmor.get(j));
            }
            for (int j = 0; j < newOffHand.size(); j++) {
                player.getInventory().offHand.set(j, newOffHand.get(j));
            }
            
            player.currentScreenHandler.sendContentUpdates();
        }

        minigame.broadcast(Text.literal("SHUFFLE! Inventories have been randomized!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
    }

    @Override
    public void loadState(JsonObject object) {
        if (object.has("ticksUntilShuffle")) {
            this.ticksUntilShuffle = object.get("ticksUntilShuffle").getAsInt();
        }
    }

    @Override
    public void saveState(JsonObject object) {
        object.addProperty("ticksUntilShuffle", this.ticksUntilShuffle);
    }

    @Override
    public void cleanup(MinecraftServer server) {
        this.countdownService.reset();
    }
}
