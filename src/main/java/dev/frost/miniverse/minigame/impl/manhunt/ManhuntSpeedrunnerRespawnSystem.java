package dev.frost.miniverse.minigame.impl.manhunt;

import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.SpectatorPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorStopReason;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProvider;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

final class ManhuntSpeedrunnerRespawnSystem {
    static final int DEFAULT_RESPAWN_DELAY_SECONDS = 5 * 60;
    private static final int RESPAWN_PROTECTION_TICKS = 3 * 20;

    private final ManhuntMinigame game;
    private final SpectatorService spectators = SpectatorService.getInstance();
    private final SpectatorPolicy policy;
    private final SpectatorTargetProvider targetProvider;
    private final Map<UUID, PendingRespawn> pendingRespawns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> protectedUntilTicks = new ConcurrentHashMap<>();
    private int respawnDelaySeconds = DEFAULT_RESPAWN_DELAY_SECONDS;

    ManhuntSpeedrunnerRespawnSystem(ManhuntMinigame game) {
        this.game = game;
        this.policy = SpectatorPolicies.teamOnly(game.teamManager(), true);
        this.targetProvider = context -> new ArrayList<>(this.game.getAliveSpeedrunners());
    }

    void reset() {
        this.pendingRespawns.clear();
        this.protectedUntilTicks.clear();
    }

    JsonObject saveRuntimeState() {
        JsonObject root = new JsonObject();
        root.addProperty("respawnDelaySeconds", this.respawnDelaySeconds);

        JsonArray pending = new JsonArray();
        for (PendingRespawn respawn : this.pendingRespawns.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("playerUuid", respawn.playerUuid().toString());
            if (respawn.targetUuid() != null) {
                entry.addProperty("targetUuid", respawn.targetUuid().toString());
            }
            entry.addProperty("respawnAtTick", respawn.respawnAtTick());
            entry.addProperty("returnMode", respawn.returnMode().getName());
            entry.addProperty("fallbackWorld", respawn.fallbackWorld().getValue().toString());
            entry.add("fallbackPos", writeBlockPos(respawn.fallbackPos()));
            pending.add(entry);
        }
        root.add("pendingRespawns", pending);

        JsonArray protection = new JsonArray();
        for (Map.Entry<UUID, Long> entry : this.protectedUntilTicks.entrySet()) {
            JsonObject object = new JsonObject();
            object.addProperty("playerUuid", entry.getKey().toString());
            object.addProperty("untilTick", entry.getValue());
            protection.add(object);
        }
        root.add("protectedUntilTicks", protection);
        return root;
    }

    void loadRuntimeState(JsonObject root) {
        if (root == null) {
            return;
        }
        this.pendingRespawns.clear();
        this.protectedUntilTicks.clear();
        this.respawnDelaySeconds = intValue(root, "respawnDelaySeconds", this.respawnDelaySeconds);

        if (root.has("pendingRespawns") && root.get("pendingRespawns").isJsonArray()) {
            for (var element : root.getAsJsonArray("pendingRespawns")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                UUID playerUuid = uuidValue(entry, "playerUuid");
                if (playerUuid == null) {
                    continue;
                }
                UUID targetUuid = uuidValue(entry, "targetUuid");
                GameMode returnMode = GameMode.byName(stringValue(entry, "returnMode", GameMode.SURVIVAL.getName()), GameMode.SURVIVAL);
                RegistryKey<World> fallbackWorld = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(stringValue(entry, "fallbackWorld", World.OVERWORLD.getValue().toString())));
                BlockPos fallbackPos = readBlockPos(entry.has("fallbackPos") && entry.get("fallbackPos").isJsonObject()
                    ? entry.getAsJsonObject("fallbackPos")
                    : null);
                this.pendingRespawns.put(playerUuid, new PendingRespawn(
                    playerUuid,
                    targetUuid,
                    longValue(entry, "respawnAtTick", 0L),
                    returnMode,
                    fallbackWorld,
                    fallbackPos
                ));
            }
        }

        if (root.has("protectedUntilTicks") && root.get("protectedUntilTicks").isJsonArray()) {
            for (var element : root.getAsJsonArray("protectedUntilTicks")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                UUID playerUuid = uuidValue(entry, "playerUuid");
                if (playerUuid != null) {
                    this.protectedUntilTicks.put(playerUuid, longValue(entry, "untilTick", 0L));
                }
            }
        }
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

        GameMode returnMode = speedrunner.interactionManager.getGameMode();
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
        this.spectators.startSpectating(
            speedrunner,
            this.policy,
            this.targetProvider,
            SpectatorMode.STANDARD,
            targetUuid,
            returnMode,
            Text.literal("Spectating teammates while waiting to respawn.").formatted(Formatting.AQUA)
        );

        this.game.broadcastManhuntMessage(
            Text.literal(speedrunner.getName().getString() + " will respawn "
                + (target == null ? "at their death location" : "near " + target.getName().getString())
                + " in " + this.respawnDelaySeconds + "s.")
                .formatted(Formatting.YELLOW)
        );
    }

    void tick(long currentTick) {
        this.protectedUntilTicks.entrySet().removeIf(entry -> entry.getValue() <= currentTick);

        if (currentTick % 20L == 0L) {
            this.sendRespawnActionBars(currentTick);
        }

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
            if (target != null) {
                this.updateSpectatorTarget(player, target.getUuid());
            } else {
                this.spectators.ensureSpectating(player);
            }
        }
    }

    void handlePlayerRespawn(ServerPlayerEntity player) {
        PendingRespawn pending = this.pendingRespawns.get(player.getUuid());
        if (pending == null) {
            return;
        }

        ServerPlayerEntity target = this.resolveTarget(pending, player);
        if (target != null) {
            this.updateSpectatorTarget(player, target.getUuid());
        }
        this.spectators.ensureSpectating(player);
    }

    void removePlayer(ServerPlayerEntity player) {
        this.pendingRespawns.remove(player.getUuid());
        this.protectedUntilTicks.remove(player.getUuid());
        this.spectators.stopSpectating(player, SpectatorStopReason.MANUAL);
    }

    boolean isProtected(ServerPlayerEntity player, long currentTick) {
        return this.protectedUntilTicks.getOrDefault(player.getUuid(), 0L) > currentTick;
    }

    boolean cycleSpectatorTarget(ServerPlayerEntity speedrunner, boolean forward) {
        return speedrunner != null && this.spectators.cycleTarget(speedrunner, forward);
    }

    @Nullable
    ServerPlayerEntity getSpectatorTarget(ServerPlayerEntity speedrunner) {
        if (speedrunner == null) {
            return null;
        }
        SpectatorSession session = this.spectators.session(speedrunner.getUuid());
        if (session == null || session.targetId() == null) {
            return null;
        }
        return this.game.getAliveSpeedrunnerByUuid(session.targetId());
    }

    void beginEliminatedSpectate(ServerPlayerEntity speedrunner) {
        ServerPlayerEntity target = this.findAliveTarget(speedrunner.getUuid());
        UUID targetUuid = target == null ? null : target.getUuid();
        this.spectators.startSpectating(
            speedrunner,
            this.policy,
            this.targetProvider,
            SpectatorMode.ELIMINATED,
            targetUuid,
            null,
            Text.literal("You are out of lives. Spectating teammates.").formatted(Formatting.RED)
        );
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
        this.updateSpectatorTarget(player, replacement.getUuid());
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

    private void completeRespawn(ServerPlayerEntity player, ServerPlayerEntity target, PendingRespawn pending, long currentTick) {
        PendingRespawn stored = this.pendingRespawns.remove(player.getUuid());
        if (stored == null) {
            return;
        }

        this.spectators.stopSpectating(player, SpectatorStopReason.RESPAWN);
        ServerWorld targetWorld = (ServerWorld) target.getEntityWorld();
        player.teleport(targetWorld, target.getX(), target.getY(), target.getZ(), Set.<PositionFlag>of(), target.getYaw(), target.getPitch());
        player.changeGameMode(stored.returnMode());
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
        PendingRespawn stored = this.pendingRespawns.remove(player.getUuid());
        if (stored == null) {
            return;
        }

        this.spectators.stopSpectating(player, SpectatorStopReason.RESPAWN);
        ServerWorld world = ((ServerWorld) player.getEntityWorld()).getServer().getWorld(stored.fallbackWorld());
        if (world == null) {
            world = (ServerWorld) player.getEntityWorld();
        }
        BlockPos pos = stored.fallbackPos();
        player.teleport(world, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, Set.<PositionFlag>of(), player.getYaw(), player.getPitch());
        player.changeGameMode(stored.returnMode());
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

    private void updateSpectatorTarget(ServerPlayerEntity player, UUID targetId) {
        if (player == null || targetId == null) {
            return;
        }
        this.spectators.setTarget(player, targetId);
        this.spectators.ensureSpectating(player);
    }

    private void sendRespawnActionBars(long currentTick) {
        if (this.pendingRespawns.isEmpty()) {
            return;
        }

        List<PendingRespawn> pendingList = new ArrayList<>(this.pendingRespawns.values());
        pendingList.sort(Comparator.comparingLong(PendingRespawn::respawnAtTick));

        StringBuilder combined = new StringBuilder();
        for (PendingRespawn pending : pendingList) {
            ServerPlayerEntity pendingPlayer = this.game.getPlayerByUuid(pending.playerUuid());
            if (pendingPlayer == null || pendingPlayer.isDisconnected()) {
                continue;
            }
            int secondsRemaining = Math.max(0, (int) ((pending.respawnAtTick() - currentTick + 19L) / 20L));
            String message = pendingPlayer.getName().getString() + " will respawn in " + secondsRemaining + "s";

            pendingPlayer.sendMessage(Text.literal(message).formatted(Formatting.YELLOW), true);

            if (combined.length() > 0) {
                combined.append(" | ");
            }
            combined.append(message);
        }

        if (combined.length() == 0) {
            return;
        }

        Text combinedText = Text.literal(combined.toString()).formatted(Formatting.YELLOW);
        for (ServerPlayerEntity speedrunner : this.game.getAliveSpeedrunners()) {
            if (speedrunner == null || speedrunner.isDisconnected()) {
                continue;
            }
            speedrunner.sendMessage(combinedText, true);
        }
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

    private static JsonObject writeBlockPos(BlockPos pos) {
        JsonObject object = new JsonObject();
        object.addProperty("x", pos.getX());
        object.addProperty("y", pos.getY());
        object.addProperty("z", pos.getZ());
        return object;
    }

    private static BlockPos readBlockPos(@Nullable JsonObject object) {
        if (object == null) {
            return BlockPos.ORIGIN;
        }
        return new BlockPos(
            intValue(object, "x", 0),
            intValue(object, "y", 0),
            intValue(object, "z", 0)
        );
    }

    private static UUID uuidValue(JsonObject object, String key) {
        String value = stringValue(object, key, "");
        if (value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsLong() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
