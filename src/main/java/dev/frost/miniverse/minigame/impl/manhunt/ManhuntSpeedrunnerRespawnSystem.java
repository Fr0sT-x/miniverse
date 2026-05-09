package dev.frost.miniverse.minigame.impl.manhunt;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ManhuntSpeedrunnerRespawnSystem {
    static final int DEFAULT_RESPAWN_DELAY_SECONDS = 5 * 60;
    private static final int RESPAWN_PROTECTION_TICKS = 3 * 20;

    private final ManhuntMinigame game;
    private final Map<UUID, PendingRespawn> pendingRespawns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> protectedUntilTicks = new ConcurrentHashMap<>();
    private int respawnDelaySeconds = DEFAULT_RESPAWN_DELAY_SECONDS;

    ManhuntSpeedrunnerRespawnSystem(ManhuntMinigame game) {
        this.game = game;
    }

    void reset() {
        this.pendingRespawns.clear();
        this.protectedUntilTicks.clear();
    }

    int getRespawnDelaySeconds() {
        return this.respawnDelaySeconds;
    }

    void setRespawnDelaySeconds(int respawnDelaySeconds) {
        this.respawnDelaySeconds = Math.max(0, respawnDelaySeconds);
    }

    boolean shouldHandleDeath(int speedrunnerCount) {
        return speedrunnerCount > 1;
    }

    boolean hasPendingRespawn(ServerPlayerEntity player) {
        return this.pendingRespawns.containsKey(player.getUuid());
    }

    void beginRespawn(ServerPlayerEntity speedrunner, long currentTick) {
        if (this.pendingRespawns.containsKey(speedrunner.getUuid())) {
            return;
        }

        ServerPlayerEntity target = this.findAliveTarget(speedrunner.getUuid());

        GameMode returnMode = speedrunner.getGameMode();
        if (returnMode == GameMode.SPECTATOR || returnMode == GameMode.CREATIVE || returnMode == GameMode.DEFAULT) {
            returnMode = GameMode.SURVIVAL;
        }

        long respawnAtTick = currentTick + (long) this.respawnDelaySeconds * 20L;
        UUID targetUuid = target == null ? null : target.getUuid();
        this.pendingRespawns.put(speedrunner.getUuid(), new PendingRespawn(
            speedrunner.getUuid(),
            targetUuid,
            respawnAtTick,
            returnMode,
            speedrunner.getEntityWorld().getRegistryKey(),
            speedrunner.getBlockPos()
        ));
        if (target != null) {
            this.applySpectatorState(speedrunner, target);
        } else {
            speedrunner.changeGameMode(GameMode.SPECTATOR);
        }

        this.game.broadcastManhuntMessage(
            Text.literal(speedrunner.getName().getString() + " will respawn "
                + (target == null ? "at their death location" : "near " + target.getName().getString())
                + " in " + this.respawnDelaySeconds + "s.")
                .formatted(Formatting.YELLOW)
        );
    }

    void tick(long currentTick) {
        this.protectedUntilTicks.entrySet().removeIf(entry -> entry.getValue() <= currentTick);

        for (PendingRespawn pending : this.pendingRespawns.values()) {
            ServerPlayerEntity player = this.game.getPlayerByUuid(pending.playerUuid());
            if (player == null || player.isDisconnected()) {
                continue;
            }

            ServerPlayerEntity target = this.resolveTarget(pending, player);
            if (target == null) {
                if (currentTick >= pending.respawnAtTick()) {
                    this.completeFallbackRespawn(player, pending, currentTick);
                }
                continue;
            }

            if (currentTick >= pending.respawnAtTick()) {
                this.completeRespawn(player, target, pending, currentTick);
            }
        }
    }

    void retargetWaitingRunners() {
        for (PendingRespawn pending : this.pendingRespawns.values()) {
            ServerPlayerEntity player = this.game.getPlayerByUuid(pending.playerUuid());
            if (player == null || player.isDisconnected()) {
                continue;
            }

            ServerPlayerEntity target = this.resolveTarget(pending, player);
            if (target == null) {
                player.changeGameMode(GameMode.SPECTATOR);
                continue;
            }

            this.applySpectatorState(player, target);
        }
    }

    void handlePlayerRespawn(ServerPlayerEntity player) {
        PendingRespawn pending = this.pendingRespawns.get(player.getUuid());
        if (pending == null) {
            return;
        }

        ServerPlayerEntity target = this.resolveTarget(pending, player);
        if (target == null) {
            player.changeGameMode(GameMode.SPECTATOR);
            return;
        }

        this.applySpectatorState(player, target);
    }

    void removePlayer(ServerPlayerEntity player) {
        this.pendingRespawns.remove(player.getUuid());
        this.protectedUntilTicks.remove(player.getUuid());
    }

    boolean isProtected(ServerPlayerEntity player, long currentTick) {
        return this.protectedUntilTicks.getOrDefault(player.getUuid(), 0L) > currentTick;
    }

    private ServerPlayerEntity resolveTarget(PendingRespawn pending, ServerPlayerEntity player) {
        ServerPlayerEntity target = pending.targetUuid() == null ? null : this.game.getAliveSpeedrunnerByUuid(pending.targetUuid());
        if (target != null && !target.isDisconnected()) {
            return target;
        }

        ServerPlayerEntity replacement = this.findAliveTarget(player.getUuid());
        if (replacement == null) {
            return null;
        }

        this.pendingRespawns.put(player.getUuid(), pending.withTarget(replacement.getUuid()));
        player.sendMessage(Text.literal("Spectating " + replacement.getName().getString() + " until respawn.").formatted(Formatting.AQUA), true);
        return replacement;
    }

    private ServerPlayerEntity findAliveTarget(UUID excludedUuid) {
        for (ServerPlayerEntity speedrunner : this.game.getAliveSpeedrunners()) {
            if (!speedrunner.getUuid().equals(excludedUuid) && !speedrunner.isDisconnected()) {
                return speedrunner;
            }
        }
        return null;
    }

    private void applySpectatorState(ServerPlayerEntity player, ServerPlayerEntity target) {
        player.changeGameMode(GameMode.SPECTATOR);
        player.setCameraEntity(target);
    }

    private void completeRespawn(ServerPlayerEntity player, ServerPlayerEntity target, PendingRespawn pending, long currentTick) {
        if (!this.pendingRespawns.remove(player.getUuid(), pending)) {
            return;
        }

        player.setCameraEntity(player);
        ServerWorld targetWorld = (ServerWorld) target.getEntityWorld();
        player.teleport(targetWorld, target.getX(), target.getY(), target.getZ(), Set.<PositionFlag>of(), target.getYaw(), target.getPitch(), true);
        player.changeGameMode(pending.returnMode());
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0F);
        player.extinguish();
        player.fallDistance = 0.0F;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, RESPAWN_PROTECTION_TICKS, 4, true, false, true));

        this.protectedUntilTicks.put(player.getUuid(), currentTick + RESPAWN_PROTECTION_TICKS);
        this.game.completeSpeedrunnerRespawn(player);
        this.game.broadcastManhuntMessage(
            Text.literal(player.getName().getString() + " respawned near " + target.getName().getString() + ".")
                .formatted(Formatting.GREEN)
        );
    }

    private void completeFallbackRespawn(ServerPlayerEntity player, PendingRespawn pending, long currentTick) {
        if (!this.pendingRespawns.remove(player.getUuid(), pending)) {
            return;
        }

        player.setCameraEntity(player);
        ServerWorld world = ((ServerWorld) player.getEntityWorld()).getServer().getWorld(pending.fallbackWorld());
        if (world == null) {
            world = (ServerWorld) player.getEntityWorld();
        }
        BlockPos pos = pending.fallbackPos();
        player.teleport(world, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, Set.<PositionFlag>of(), player.getYaw(), player.getPitch(), true);
        player.changeGameMode(pending.returnMode());
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0F);
        player.extinguish();
        player.fallDistance = 0.0F;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, RESPAWN_PROTECTION_TICKS, 4, true, false, true));

        this.protectedUntilTicks.put(player.getUuid(), currentTick + RESPAWN_PROTECTION_TICKS);
        this.game.completeSpeedrunnerRespawn(player);
        this.game.broadcastManhuntMessage(
            Text.literal(player.getName().getString() + " respawned at their death location.")
                .formatted(Formatting.GREEN)
        );
    }

    private record PendingRespawn(
        UUID playerUuid,
        UUID targetUuid,
        long respawnAtTick,
        GameMode returnMode,
        RegistryKey<World> fallbackWorld,
        BlockPos fallbackPos
    ) {
        private PendingRespawn withTarget(UUID targetUuid) {
            return new PendingRespawn(this.playerUuid, targetUuid, this.respawnAtTick, this.returnMode, this.fallbackWorld, this.fallbackPos);
        }
    }
}
