package dev.frost.miniverse.minigame.impl.horde.revive;

import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.minigame.impl.horde.HordeSurvivalMinigame;
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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DownedPlayerManager {
    public static final int BLEEDOUT_TICKS = 30 * 20; // 30 seconds
    public static final float REVIVE_THRESHOLD = 100.0f; // 100 ticks = 5 seconds
    public static final double REVIVE_RADIUS_SQ = 9.0; // 3 blocks

    private final HordeSurvivalMinigame minigame;
    private final Map<UUID, DownedState> downedPlayers = new ConcurrentHashMap<>();

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

    public DownedPlayerManager(HordeSurvivalMinigame minigame) {
        this.minigame = minigame;
    }

    public boolean isDowned(UUID playerUuid) {
        return this.downedPlayers.containsKey(playerUuid);
    }

    public void downPlayer(ServerPlayerEntity player) {
        if (isDowned(player.getUuid())) return;

        ServerWorld world = player.getServerWorld();
        player.setHealth(2.0f);

        // Freeze player inputs and movement
        FreezeService.getInstance().freeze(player, FreezeReason.DOWNED_PLAYER);

        // Slow & glowing
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, BLEEDOUT_TICKS, 4, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, BLEEDOUT_TICKS, 0, false, false, true));

        // Create overhead marker
        ArmorStandEntity marker = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        marker.setPosition(player.getX(), player.getY() + 1.2, player.getZ());
        marker.setCustomName(Text.literal("§c☠ DOWNED (30s) ☠"));
        marker.setCustomNameVisible(true);
        marker.setInvisible(true);
        marker.setInvulnerable(true);
        marker.setNoGravity(true);
        world.spawnEntity(marker);

        this.downedPlayers.put(player.getUuid(), new DownedState(player.getUuid(), marker));

        // Send screen title to downed player
        player.sendMessage(Text.literal("☠ YOU ARE DOWNED!").formatted(Formatting.RED, Formatting.BOLD), false);
        this.minigame.broadcast(Text.literal("☠ " + player.getName().getString() + " is DOWNED! Stand near them to revive!").formatted(Formatting.RED, Formatting.BOLD));
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 0.7f);
    }

    public void tick(ServerWorld world, List<ServerPlayerEntity> survivors) {
        if (this.downedPlayers.isEmpty()) return;

        Iterator<Map.Entry<UUID, DownedState>> it = this.downedPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DownedState> entry = it.next();
            DownedState state = entry.getValue();
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(state.playerUuid);

            if (player == null || !player.isAlive()) {
                if (state.marker != null) state.marker.discard();
                it.remove();
                continue;
            }

            state.bleedoutTicks--;

            // Keep marker positioned directly above player
            if (state.marker != null) {
                state.marker.setPosition(player.getX(), player.getY() + 1.2, player.getZ());
            }

            // Check if any alive, non-downed survivor is within 3 blocks
            ServerPlayerEntity reviver = null;
            for (ServerPlayerEntity other : survivors) {
                if (!other.getUuid().equals(state.playerUuid) && !isDowned(other.getUuid())) {
                    if (other.squaredDistanceTo(player) <= REVIVE_RADIUS_SQ) {
                        reviver = other;
                        break;
                    }
                }
            }

            if (reviver != null) {
                state.reviveProgress = Math.min(REVIVE_THRESHOLD, state.reviveProgress + 1.0f);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.5, player.getZ(), 2, 0.3, 0.3, 0.3, 0.05);

                int percent = (int) ((state.reviveProgress / REVIVE_THRESHOLD) * 100);
                String bar = buildProgressBar(percent);

                Text reviveMsg = Text.literal("§a✚ REVIVING: " + bar + " §e" + percent + "%");
                player.sendMessage(reviveMsg, true);
                reviver.sendMessage(reviveMsg, true);

                if (state.marker != null) {
                    state.marker.setCustomName(Text.literal("§a✚ REVIVING [" + percent + "%]"));
                }
            } else {
                if (state.reviveProgress > 0) {
                    state.reviveProgress = Math.max(0.0f, state.reviveProgress - 0.5f);
                }
                int secLeft = Math.max(0, state.bleedoutTicks / 20);
                player.sendMessage(Text.literal("§c☠ DOWNED - Bleeding out in " + secLeft + "s! Need revive!"), true);
                if (state.marker != null) {
                    state.marker.setCustomName(Text.literal("§c☠ DOWNED (" + secLeft + "s) ☠"));
                }
            }

            // Check if revive completed
            if (state.reviveProgress >= REVIVE_THRESHOLD) {
                revivePlayer(player, reviver, state);
                it.remove();
            } else if (state.bleedoutTicks <= 0) {
                // Bleedout expired -> player dies
                if (state.marker != null) state.marker.discard();
                FreezeService.getInstance().unfreeze(player, FreezeReason.DOWNED_PLAYER);
                it.remove();
                this.minigame.onSurvivorDied(player);
            }
        }
    }

    private void revivePlayer(ServerPlayerEntity player, ServerPlayerEntity reviver, DownedState state) {
        if (state.marker != null) state.marker.discard();
        FreezeService.getInstance().unfreeze(player, FreezeReason.DOWNED_PLAYER);

        player.clearStatusEffects();
        player.setHealth(player.getMaxHealth() / 2.0f); // 50% health

        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
        world.spawnParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.5, 0.3, 0.05);

        String reviverName = reviver != null ? reviver.getName().getString() : "a teammate";
        this.minigame.broadcast(Text.literal("✔ " + player.getName().getString() + " was REVIVED by " + reviverName + "!").formatted(Formatting.GREEN, Formatting.BOLD));

        if (reviver != null) {
            this.minigame.getBountyManager().onTeammateRevived(reviver.getUuid());
        }
    }

    public boolean allSurvivorsDowned(List<ServerPlayerEntity> survivors) {
        if (survivors.isEmpty()) return false;
        for (ServerPlayerEntity p : survivors) {
            if (!isDowned(p.getUuid())) {
                return false;
            }
        }
        return true;
    }

    private static String buildProgressBar(int percent) {
        int totalBars = 10;
        int filled = (percent * totalBars) / 100;
        return "[" + "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, totalBars - filled)) + "]";
    }

    public void cleanup(ServerWorld world) {
        for (DownedState state : this.downedPlayers.values()) {
            if (state.marker != null) state.marker.discard();
            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(state.playerUuid);
            if (p != null) {
                FreezeService.getInstance().unfreeze(p, FreezeReason.DOWNED_PLAYER);
            }
        }
        this.downedPlayers.clear();
    }
}
