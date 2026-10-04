package dev.frost.miniverse.minigame.impl.hideandseek.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HideAndSeekTauntManager {
    private static final List<SoundEvent> TAUNT_SOUNDS = List.of(
        SoundEvents.ENTITY_CAT_AMBIENT,
        SoundEvents.ENTITY_SHEEP_AMBIENT,
        SoundEvents.ENTITY_VILLAGER_AMBIENT,
        SoundEvents.ENTITY_BAT_AMBIENT,
        SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
        SoundEvents.BLOCK_ANVIL_LAND,
        SoundEvents.BLOCK_BELL_USE,
        SoundEvents.ENTITY_GOAT_SCREAMING_AMBIENT,
        SoundEvents.ENTITY_CREEPER_PRIMED,
        SoundEvents.ENTITY_PIG_AMBIENT,
        SoundEvents.ENTITY_CHICKEN_AMBIENT
    );

    private final ServerWorld world;
    private final int cooldownSeconds;
    private final Map<UUID, Long> lastTauntTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> hiderPoints = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public HideAndSeekTauntManager(ServerWorld world, int cooldownSeconds) {
        this.world = world;
        this.cooldownSeconds = cooldownSeconds;
    }

    public static ItemStack createTauntItem() {
        ItemStack item = new ItemStack(Items.NOTE_BLOCK);
        item.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Taunt §7(Right-Click)").formatted(Formatting.GOLD, Formatting.BOLD));
        return item;
    }

    public boolean tryTaunt(ServerPlayerEntity player) {
        long now = System.currentTimeMillis();
        Long last = this.lastTauntTimes.get(player.getUuid());

        if (last != null) {
            long elapsedSeconds = (now - last) / 1000;
            if (elapsedSeconds < this.cooldownSeconds) {
                long remaining = this.cooldownSeconds - elapsedSeconds;
                player.sendMessage(Text.literal("§cTaunt is on cooldown! (" + remaining + "s left)"), true);
                player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5F, 1.2F);
                return false;
            }
        }

        this.lastTauntTimes.put(player.getUuid(), now);
        this.addPoints(player.getUuid(), 50);

        SoundEvent sound = TAUNT_SOUNDS.get(this.random.nextInt(TAUNT_SOUNDS.size()));
        this.world.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, 1.8F, 1.0F);
        this.world.spawnParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.4, 0.4, 0.4, 0.5);
        this.world.spawnParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 0.8, player.getZ(), 10, 0.3, 0.3, 0.3, 0.1);

        player.sendMessage(Text.literal("§e🎵 TAUNT USED! +50 Points!").formatted(Formatting.GOLD, Formatting.BOLD), true);
        return true;
    }

    public void addPoints(UUID uuid, int points) {
        this.hiderPoints.merge(uuid, points, Integer::sum);
    }

    public int getPoints(UUID uuid) {
        return this.hiderPoints.getOrDefault(uuid, 0);
    }

    public Map<UUID, Integer> getAllPoints() {
        return java.util.Collections.unmodifiableMap(this.hiderPoints);
    }

    public int getCooldownRemaining(UUID uuid) {
        Long last = this.lastTauntTimes.get(uuid);
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000;
        return (int) Math.max(0, this.cooldownSeconds - elapsed);
    }

    public void clear() {
        this.lastTauntTimes.clear();
        this.hiderPoints.clear();
    }
}
