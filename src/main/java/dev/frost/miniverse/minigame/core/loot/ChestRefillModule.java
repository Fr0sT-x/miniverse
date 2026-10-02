package dev.frost.miniverse.minigame.core.loot;

import dev.frost.miniverse.minigame.core.FrameworkModule;
import dev.frost.miniverse.minigame.core.countdown.CountdownService;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Manages timed chest refill events, countdown sound/title announcements,
 * and repopulation of island and center/feast chests across matches.
 */
public class ChestRefillModule implements FrameworkModule {
    private final ServerWorld world;
    private final List<BlockPos> islandChests;
    private final List<BlockPos> midChests;
    private final ChestLootTable islandLootTable;
    private final ChestLootTable midLootTable;
    private final Supplier<Collection<ServerPlayerEntity>> playersSupplier;
    private final int intervalSeconds;
    private final int maxRefills;
    private final CountdownService countdownService = new CountdownService();
    private final Random random = Random.create();

    private int ticksRemaining;
    private int refillCount = 0;
    private boolean active = true;

    public ChestRefillModule(
        ServerWorld world,
        List<BlockPos> islandChests,
        List<BlockPos> midChests,
        ChestLootTable islandLootTable,
        ChestLootTable midLootTable,
        Supplier<Collection<ServerPlayerEntity>> playersSupplier,
        int intervalSeconds,
        int maxRefills
    ) {
        this.world = world;
        this.islandChests = islandChests == null ? Collections.emptyList() : List.copyOf(islandChests);
        this.midChests = midChests == null ? Collections.emptyList() : List.copyOf(midChests);
        this.islandLootTable = islandLootTable;
        this.midLootTable = midLootTable;
        this.playersSupplier = playersSupplier == null ? Collections::emptyList : playersSupplier;
        this.intervalSeconds = Math.max(10, intervalSeconds);
        this.maxRefills = Math.max(1, maxRefills);
        this.ticksRemaining = this.intervalSeconds * 20;
    }

    /**
     * Immediately fills all tracked chests with fresh loot (typically called at match start).
     */
    public void populateAll(boolean clearFirst) {
        if (this.world == null) return;

        if (this.islandLootTable != null) {
            for (BlockPos pos : this.islandChests) {
                ChestFiller.fillChestAt(this.world, pos, this.islandLootTable, this.random, clearFirst);
            }
        }

        if (this.midLootTable != null) {
            for (BlockPos pos : this.midChests) {
                ChestFiller.fillChestAt(this.world, pos, this.midLootTable, this.random, clearFirst);
            }
        }
    }

    public void tick() {
        if (!this.active || this.refillCount >= this.maxRefills || this.world == null) {
            return;
        }

        if (this.ticksRemaining > 0) {
            this.ticksRemaining--;
            int seconds = this.ticksRemaining / 20;

            if (this.ticksRemaining % 20 == 0) {
                Collection<ServerPlayerEntity> players = this.playersSupplier.get();
                if (seconds == 60 || seconds == 30 || seconds == 15) {
                    for (ServerPlayerEntity player : players) {
                        player.sendMessage(Text.literal("⚡ Chests will refill in " + seconds + " seconds!").formatted(Formatting.GOLD), false);
                    }
                } else if (seconds <= 5 && seconds > 0) {
                    this.countdownService.announceVisibleCountdown(
                        players,
                        seconds,
                        5,
                        Text.literal("CHEST REFILL").formatted(Formatting.YELLOW, Formatting.BOLD),
                        Text.literal("Refill in " + seconds + "...").formatted(Formatting.GOLD),
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()
                    );
                }
            }

            if (this.ticksRemaining == 0) {
                this.executeRefill();
            }
        }
    }

    private void executeRefill() {
        this.refillCount++;
        this.populateAll(true);
        this.countdownService.reset();

        Collection<ServerPlayerEntity> players = this.playersSupplier.get();
        for (ServerPlayerEntity player : players) {
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("CHEST REFILL!").formatted(Formatting.GREEN, Formatting.BOLD)));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("All chests have been refilled with fresh loot!").formatted(Formatting.YELLOW)));
            this.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8F, 1.2F);
            this.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }

        // Particle bursts at chest locations
        for (BlockPos pos : this.midChests) {
            this.world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.05);
        }
        for (BlockPos pos : this.islandChests) {
            this.world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 4, 0.25, 0.25, 0.25, 0.05);
        }

        if (this.refillCount < this.maxRefills) {
            this.ticksRemaining = this.intervalSeconds * 20;
        } else {
            this.active = false;
        }
    }

    public int getSecondsUntilNextRefill() {
        return Math.max(0, this.ticksRemaining / 20);
    }

    public int getRefillCount() {
        return this.refillCount;
    }

    public int getMaxRefills() {
        return this.maxRefills;
    }

    public boolean isRefillsExhausted() {
        return this.refillCount >= this.maxRefills;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void cleanup(net.minecraft.server.MinecraftServer server) {
        this.active = false;
        this.countdownService.reset();
    }
}
