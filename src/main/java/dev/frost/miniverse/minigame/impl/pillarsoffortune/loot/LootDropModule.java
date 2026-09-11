package dev.frost.miniverse.minigame.impl.pillarsoffortune.loot;

import dev.frost.miniverse.minigame.core.FrameworkModule;
import dev.frost.miniverse.minigame.core.countdown.CountdownService;
import dev.frost.miniverse.minigame.impl.pillarsoffortune.PillarsOfFortuneMinigame;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.List;

public class LootDropModule implements FrameworkModule {
    private final PillarsOfFortuneMinigame minigame;
    private final LootTable lootTable;
    private final CountdownService countdownService = new CountdownService();
    private final int intervalSeconds;
    private int ticksUntilNextDrop;
    private final Random random = Random.create();

    public LootDropModule(PillarsOfFortuneMinigame minigame, LootTable lootTable, int intervalSeconds) {
        this.minigame = minigame;
        this.lootTable = lootTable;
        this.intervalSeconds = intervalSeconds;
        this.ticksUntilNextDrop = intervalSeconds * 20;
    }

    public void tick() {
        if (this.ticksUntilNextDrop > 0) {
            this.ticksUntilNextDrop--;
            int secondsRemaining = this.ticksUntilNextDrop / 20;
            if (this.ticksUntilNextDrop % 20 == 0 && secondsRemaining <= 10 && secondsRemaining > 0) {
                this.countdownService.announceVisibleCountdown(
                    this.minigame.getAliveParticipants(),
                    secondsRemaining,
                    5, 
                    Text.literal("Loot Drop!").formatted(Formatting.AQUA),
                    Text.literal("Loot drop in " + secondsRemaining + "s!").formatted(Formatting.GOLD),
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()
                );
            }
            if (this.ticksUntilNextDrop == 0) {
                this.dropLoot();
                this.ticksUntilNextDrop = this.intervalSeconds * 20;
                this.countdownService.reset();
            }
        }
    }

    private void dropLoot() {
        List<ServerPlayerEntity> alivePlayers = this.minigame.getAliveParticipants();
        for (ServerPlayerEntity player : alivePlayers) {
            List<ItemStack> items = this.lootTable.roll(this.random, 3); // 3 items per drop
            for (ItemStack item : items) {
                if (!player.getInventory().insertStack(item)) {
                    player.dropItem(item, false);
                }
            }
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.sendMessage(Text.literal("Loot has been dropped!").formatted(Formatting.GREEN, Formatting.BOLD), false);
        }
    }

    public int getSecondsUntilNextDrop() {
        return this.ticksUntilNextDrop / 20;
    }

    public void setSecondsUntilNextDrop(int seconds) {
        this.ticksUntilNextDrop = seconds * 20;
    }

    @Override
    public void cleanup(MinecraftServer server) {
        this.countdownService.reset();
    }
}
