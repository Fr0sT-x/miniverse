package dev.frost.miniverse.minigame.impl.zombies.revive;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.minigame.impl.zombies.perk.PlayerPerk;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class ZombiesReviveManager {
    public static final int BLEEDOUT_TICKS = 30 * 20; // 30 seconds
    public static final float REVIVE_THRESHOLD = 100.0f; // 100 ticks = 5 seconds
    public static final double REVIVE_RADIUS_SQ = 9.0; // 3 blocks

    public static class DownedState {
        public final UUID playerUuid;
        public int bleedoutTicks;
        public float reviveProgress;
        public ArmorStandEntity marker;

        public DownedState(UUID playerUuid, ArmorStandEntity marker) {
            this.playerUuid = playerUuid;
            this.bleedoutTicks = BLEEDOUT_TICKS;
            this.reviveProgress = 0.0f;
            this.marker = marker;
        }
    }

    private final ServerWorld world;
    private final BiConsumer<ServerPlayerEntity, Integer> goldAwarder;
    private final BiPredicate<ServerPlayerEntity, PlayerPerk> perkChecker;
    private final Consumer<Text> broadcastCallback;
    private final Map<UUID, DownedState> downedPlayers = new ConcurrentHashMap<>();
    private Consumer<ServerPlayerEntity> onReviveOrRespawnCallback;

    public ZombiesReviveManager(
        ServerWorld world,
        BiConsumer<ServerPlayerEntity, Integer> goldAwarder,
        BiPredicate<ServerPlayerEntity, PlayerPerk> perkChecker,
        Consumer<Text> broadcastCallback
    ) {
        this.world = world;
        this.goldAwarder = goldAwarder;
        this.perkChecker = perkChecker;
        this.broadcastCallback = broadcastCallback;
    }

    public void setOnReviveOrRespawnCallback(Consumer<ServerPlayerEntity> callback) {
        this.onReviveOrRespawnCallback = callback;
    }

    public boolean isDowned(UUID uuid) {
        return this.downedPlayers.containsKey(uuid);
    }

    public void downPlayer(ServerPlayerEntity player) {
        if (isDowned(player.getUuid())) return;

        player.setHealth(2.0f);
        FreezeService.getInstance().freeze(player, FreezeReason.DOWNED_PLAYER);

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, BLEEDOUT_TICKS, 4, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, BLEEDOUT_TICKS, 0, false, false, true));

        ArmorStandEntity marker = new ArmorStandEntity(EntityType.ARMOR_STAND, this.world);
        marker.setPosition(player.getX(), player.getY() + 1.2, player.getZ());
        marker.setCustomName(Text.literal("☠ DOWNED (30s) ☠").formatted(Formatting.RED, Formatting.BOLD));
        marker.setCustomNameVisible(true);
        marker.setInvisible(true);
        marker.setInvulnerable(true);
        marker.setNoGravity(true);
        byte flags = marker.getDataTracker().get(ArmorStandEntity.ARMOR_STAND_FLAGS);
        marker.getDataTracker().set(ArmorStandEntity.ARMOR_STAND_FLAGS, (byte) (flags | ArmorStandEntity.MARKER_FLAG));
        this.world.spawnEntity(marker);

        this.downedPlayers.put(player.getUuid(), new DownedState(player.getUuid(), marker));

        player.sendMessage(Text.literal("☠ YOU ARE DOWNED! Sneak near teammates to get revived!").formatted(Formatting.RED, Formatting.BOLD), false);
        this.broadcastCallback.accept(Text.literal("☠ " + player.getName().getString() + " is DOWNED!").formatted(Formatting.RED, Formatting.BOLD));
        this.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.2f, 0.7f);
    }

    public void tick(List<ServerPlayerEntity> survivors) {
        if (this.downedPlayers.isEmpty()) return;

        Iterator<Map.Entry<UUID, DownedState>> it = this.downedPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DownedState> entry = it.next();
            DownedState state = entry.getValue();
            ServerPlayerEntity player = this.world.getServer().getPlayerManager().getPlayer(state.playerUuid);

            if (player == null || !player.isAlive()) {
                if (state.marker != null) state.marker.discard();
                it.remove();
                continue;
            }

            state.bleedoutTicks--;

            // Find nearby reviving teammate
            ServerPlayerEntity reviver = null;
            for (ServerPlayerEntity p : survivors) {
                if (!p.getUuid().equals(player.getUuid()) && !p.isSpectator() && !isDowned(p.getUuid()) && p.isSneaking()) {
                    if (p.squaredDistanceTo(player) <= REVIVE_RADIUS_SQ) {
                        reviver = p;
                        break;
                    }
                }
            }

            if (reviver != null) {
                float rate = 1.0f;
                if (this.perkChecker.test(reviver, PlayerPerk.FAST_REVIVE)) {
                    rate = 3.0f; // 3x faster with Fast Revive perk
                }
                state.reviveProgress += rate;
                this.world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.8, player.getZ(), 2, 0.3, 0.3, 0.3, 0.05);

                if (state.reviveProgress >= REVIVE_THRESHOLD) {
                    revivePlayer(player, reviver);
                    if (state.marker != null) state.marker.discard();
                    it.remove();
                    continue;
                }
            } else {
                state.reviveProgress = Math.max(0.0f, state.reviveProgress - 0.5f);
            }

            // Update marker
            if (state.marker != null && !state.marker.isRemoved()) {
                int sec = Math.max(0, state.bleedoutTicks / 20);
                int progressBars = (int) ((state.reviveProgress / REVIVE_THRESHOLD) * 10);
                String bar = "§a" + "■".repeat(progressBars) + "§7" + "□".repeat(10 - progressBars);
                state.marker.setCustomName(Text.literal("☠ DOWNED (" + sec + "s) [" + bar + "§r]"));
            }

            // Bleedout check
            if (state.bleedoutTicks <= 0) {
                FreezeService.getInstance().unfreeze(player, FreezeReason.DOWNED_PLAYER);
                player.clearStatusEffects();
                player.changeGameMode(GameMode.SPECTATOR);
                if (state.marker != null) state.marker.discard();
                it.remove();

                this.broadcastCallback.accept(Text.literal("☠ " + player.getName().getString() + " bled out and is spectating until round end.").formatted(Formatting.DARK_RED));
                this.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 0.8f, 1.2f);
            }
        }
    }

    public void revivePlayer(ServerPlayerEntity victim, ServerPlayerEntity reviver) {
        FreezeService.getInstance().unfreeze(victim, FreezeReason.DOWNED_PLAYER);
        victim.clearStatusEffects();
        if (this.onReviveOrRespawnCallback != null) {
            this.onReviveOrRespawnCallback.accept(victim);
        }
        victim.setHealth(victim.getMaxHealth() / 2.0f);

        this.world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
        victim.sendMessage(Text.literal("✔ You were revived by " + (reviver != null ? reviver.getName().getString() : "a teammate") + "!").formatted(Formatting.GREEN, Formatting.BOLD), false);

        if (reviver != null) {
            this.goldAwarder.accept(reviver, 100);
            this.broadcastCallback.accept(Text.literal("✔ " + reviver.getName().getString() + " revived " + victim.getName().getString() + "!").formatted(Formatting.GREEN));
        }
    }

    public void respawnAllAtRoundEnd(List<ServerPlayerEntity> survivors, MapPosition spawnPos) {
        for (ServerPlayerEntity p : survivors) {
            boolean wasDownedOrSpec = isDowned(p.getUuid()) || p.isSpectator();
            if (isDowned(p.getUuid())) {
                DownedState state = this.downedPlayers.remove(p.getUuid());
                if (state != null && state.marker != null) state.marker.discard();
                FreezeService.getInstance().unfreeze(p, FreezeReason.DOWNED_PLAYER);
                p.clearStatusEffects();
            }

            if (p.isSpectator()) {
                p.changeGameMode(GameMode.ADVENTURE);
                if (spawnPos != null) {
                    p.teleport(this.world, spawnPos.x(), spawnPos.y(), spawnPos.z(), spawnPos.yaw(), spawnPos.pitch());
                }
            }

            if (wasDownedOrSpec && this.onReviveOrRespawnCallback != null) {
                this.onReviveOrRespawnCallback.accept(p);
            }

            p.setHealth(p.getMaxHealth());
            p.getHungerManager().setFoodLevel(20);
        }
    }

    public boolean isAllDeadOrDowned(List<ServerPlayerEntity> survivors) {
        if (survivors.isEmpty()) return false;
        for (ServerPlayerEntity p : survivors) {
            if (!p.isSpectator() && !isDowned(p.getUuid()) && p.isAlive()) {
                return false;
            }
        }
        return true;
    }

    public void cleanup() {
        for (DownedState state : this.downedPlayers.values()) {
            if (state.marker != null) state.marker.discard();
            ServerPlayerEntity p = this.world.getServer().getPlayerManager().getPlayer(state.playerUuid);
            if (p != null) {
                FreezeService.getInstance().unfreeze(p, FreezeReason.DOWNED_PLAYER);
                p.clearStatusEffects();
            }
        }
        this.downedPlayers.clear();
    }
}
