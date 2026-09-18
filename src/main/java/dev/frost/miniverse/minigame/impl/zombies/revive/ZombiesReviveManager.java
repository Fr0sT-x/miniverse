package dev.frost.miniverse.minigame.impl.zombies.revive;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.core.freeze.DownedPlayerTracker;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.minigame.impl.zombies.perk.PlayerPerk;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
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
    public static final float REVIVE_THRESHOLD = 100.0f; // 100 ticks = 5.0 seconds
    public static final double REVIVE_RADIUS_SQ = 9.0; // 3 blocks

    public static class DownedState {
        public final UUID playerUuid;
        public int bleedoutTicks;
        public float reviveProgress;
        public ArmorStandEntity marker;
        public UUID lastReviver;

        public DownedState(UUID playerUuid, ArmorStandEntity marker) {
            this.playerUuid = playerUuid;
            this.bleedoutTicks = BLEEDOUT_TICKS;
            this.reviveProgress = 0.0f;
            this.marker = marker;
            this.lastReviver = null;
        }
    }

    private final ServerWorld world;
    private final BiConsumer<ServerPlayerEntity, Integer> goldAwarder;
    private final BiPredicate<ServerPlayerEntity, PlayerPerk> perkChecker;
    private final Consumer<Text> broadcastCallback;
    private final Map<UUID, DownedState> downedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> revivedInvulnerability = new ConcurrentHashMap<>();
    private Consumer<ServerPlayerEntity> onReviveOrRespawnCallback;
    private int bleedoutSeconds = 30;

    public void setBleedoutSeconds(int bleedoutSeconds) {
        this.bleedoutSeconds = bleedoutSeconds > 0 ? bleedoutSeconds : 30;
    }

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

    public boolean isInvulnerable(UUID uuid) {
        Long until = this.revivedInvulnerability.get(uuid);
        return until != null && System.currentTimeMillis() < until;
    }

    public void grantReviveInvulnerability(UUID uuid, long durationMs) {
        this.revivedInvulnerability.put(uuid, System.currentTimeMillis() + durationMs);
    }

    private void broadcastDownedState(UUID uuid, boolean downed) {
        NetworkConstants.DownedStatePayload payload = new NetworkConstants.DownedStatePayload(uuid, downed);
        if (this.world != null && this.world.getServer() != null) {
            for (ServerPlayerEntity p : this.world.getServer().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    public void downPlayer(ServerPlayerEntity player) {
        if (isDowned(player.getUuid())) return;

        player.setHealth(2.0f);
        FreezeService.getInstance().freeze(player, FreezeReason.DOWNED_PLAYER);
        DownedPlayerTracker.setDowned(player.getUuid(), true);
        player.setPose(EntityPose.SWIMMING);
        broadcastDownedState(player.getUuid(), true);

        int ticks = this.bleedoutSeconds * 20;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 4, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, ticks, 0, false, false, true));

        ArmorStandEntity marker = new ArmorStandEntity(EntityType.ARMOR_STAND, this.world);
        marker.setPosition(player.getX(), player.getY() + 1.2, player.getZ());
        marker.setCustomName(Text.literal("☠ DOWNED (" + this.bleedoutSeconds + "s) ☠").formatted(Formatting.RED, Formatting.BOLD));
        marker.setCustomNameVisible(true);
        marker.setInvisible(true);
        marker.setInvulnerable(true);
        marker.setNoGravity(true);
        byte flags = marker.getDataTracker().get(ArmorStandEntity.ARMOR_STAND_FLAGS);
        marker.getDataTracker().set(ArmorStandEntity.ARMOR_STAND_FLAGS, (byte) (flags | ArmorStandEntity.MARKER_FLAG));
        this.world.spawnEntity(marker);

        DownedState state = new DownedState(player.getUuid(), marker);
        state.bleedoutTicks = ticks;
        this.downedPlayers.put(player.getUuid(), state);

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
                DownedPlayerTracker.setDowned(state.playerUuid, false);
                broadcastDownedState(state.playerUuid, false);
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
                state.lastReviver = reviver.getUuid();
                this.world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.8, player.getZ(), 2, 0.3, 0.3, 0.3, 0.05);

                // Send live revive progress HUD to reviver
                int progressBars = Math.min(10, (int) ((state.reviveProgress / REVIVE_THRESHOLD) * 10));
                String bar = "§a" + "■".repeat(progressBars) + "§7" + "□".repeat(10 - progressBars);
                float remainingSec = Math.max(0.0f, (REVIVE_THRESHOLD - state.reviveProgress) / (rate * 20.0f));
                reviver.sendMessage(Text.literal("Reviving " + player.getName().getString() + "... [" + bar + "§r] (" + String.format("%.1f", remainingSec) + "s)").formatted(Formatting.GREEN), true);

                if (state.reviveProgress >= REVIVE_THRESHOLD) {
                    reviver.sendMessage(Text.empty(), true);
                    revivePlayer(player, reviver);
                    if (state.marker != null) state.marker.discard();
                    it.remove();
                    continue;
                }
            } else {
                if (state.lastReviver != null) {
                    ServerPlayerEntity prev = this.world.getServer().getPlayerManager().getPlayer(state.lastReviver);
                    if (prev != null) {
                        prev.sendMessage(Text.empty(), true);
                    }
                    state.lastReviver = null;
                }
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
                DownedPlayerTracker.setDowned(player.getUuid(), false);
                player.setPose(EntityPose.STANDING);
                broadcastDownedState(player.getUuid(), false);

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
        DownedPlayerTracker.setDowned(victim.getUuid(), false);
        victim.setPose(EntityPose.STANDING);
        broadcastDownedState(victim.getUuid(), false);

        victim.clearStatusEffects();
        if (this.onReviveOrRespawnCallback != null) {
            this.onReviveOrRespawnCallback.accept(victim);
        }
        victim.setHealth(victim.getMaxHealth() / 2.0f);

        // 2 seconds of invulnerability
        grantReviveInvulnerability(victim.getUuid(), 2000L);
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 4, false, false, true));

        // TNT Explosion smoke effect & audio
        this.world.spawnParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 0.5, victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        this.world.spawnParticles(ParticleTypes.LARGE_SMOKE, victim.getX(), victim.getY() + 0.5, victim.getZ(), 20, 0.4, 0.4, 0.4, 0.08);
        this.world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
        this.world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.5f);

        victim.sendMessage(Text.literal("✔ You were revived by " + (reviver != null ? reviver.getName().getString() : "a teammate") + "! (2s invulnerability)").formatted(Formatting.GREEN, Formatting.BOLD), false);

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
                DownedPlayerTracker.setDowned(p.getUuid(), false);
                p.setPose(EntityPose.STANDING);
                broadcastDownedState(p.getUuid(), false);
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
        MinecraftServer server = this.world != null ? this.world.getServer() : null;
        for (DownedState state : this.downedPlayers.values()) {
            if (state.marker != null) state.marker.discard();
            if (server != null) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(state.playerUuid);
                if (p != null) {
                    FreezeService.getInstance().unfreeze(p, FreezeReason.DOWNED_PLAYER);
                    p.setPose(EntityPose.STANDING);
                    p.clearStatusEffects();
                }
            }
            broadcastDownedState(state.playerUuid, false);
        }
        if (this.world != null) {
            for (ServerPlayerEntity p : this.world.getPlayers()) {
                p.setPose(EntityPose.STANDING);
                broadcastDownedState(p.getUuid(), false);
            }
        }
        DownedPlayerTracker.clear();
        this.downedPlayers.clear();
        this.revivedInvulnerability.clear();
    }
}
