package dev.frost.miniverse.minigame.core.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerStateStore {
    private PlayerStateStore() {
    }

    public static JsonArray capture(MinigameRuntime runtime) {
        return capture(runtime, null);
    }

    public static JsonArray capture(MinigameRuntime runtime, JsonArray previousPlayers) {
        JsonArray players = new JsonArray();
        if (runtime == null) {
            return players;
        }

        Map<UUID, JsonObject> previousByPlayer = previousSnapshots(previousPlayers);
        Map<UUID, JsonObject> merged = new LinkedHashMap<>();
        Set<UUID> participantIds = runtime.context().participantIds();
        for (ServerPlayerEntity player : runtime.context().liveParticipants()) {
            if (participantIds.isEmpty() || participantIds.contains(player.getUuid())) {
                merged.put(player.getUuid(), PlayerStateSnapshot.capture(player).toJson());
            }
        }

        for (UUID participantId : participantIds) {
            if (!merged.containsKey(participantId) && previousByPlayer.containsKey(participantId)) {
                merged.put(participantId, previousByPlayer.get(participantId).deepCopy());
            }
        }

        if (merged.isEmpty() && shouldPreservePreviousWithoutRoster(runtime) && !previousByPlayer.isEmpty()) {
            for (JsonObject snapshot : previousByPlayer.values()) {
                players.add(snapshot.deepCopy());
            }
            return players;
        }

        for (JsonObject snapshot : merged.values()) {
            players.add(snapshot);
        }
        return players;
    }

    public static int restore(MinigameRuntime runtime, JsonArray players) {
        MinecraftServer server = runtime == null ? null : runtime.context().nullableServer();
        if (server == null || players == null) {
            return 0;
        }

        int restored = 0;
        for (var element : players) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            PlayerStateSnapshot snapshot = PlayerStateSnapshot.fromJson(object);
            if (snapshot == null) {
                continue;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(snapshot.playerId());
            if (player == null) {
                continue;
            }
            try {
                snapshot.restore(server, player);
                restored++;
            } catch (RuntimeException e) {
                Miniverse.LOGGER.warn("Failed to restore player state for {} ({})", snapshot.playerName(), snapshot.playerId(), e);
            }
        }
        return restored;
    }

    public static boolean restore(MinigameRuntime runtime, JsonArray players, ServerPlayerEntity player) {
        MinecraftServer server = runtime == null ? null : runtime.context().nullableServer();
        if (server == null || players == null || player == null) {
            return false;
        }

        UUID playerId = player.getUuid();
        for (var element : players) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            PlayerStateSnapshot snapshot = PlayerStateSnapshot.fromJson(object);
            if (snapshot == null || !snapshot.playerId().equals(playerId)) {
                continue;
            }
            try {
                snapshot.restore(server, player);
                return true;
            } catch (RuntimeException e) {
                Miniverse.LOGGER.warn("Failed to restore player state for {} ({})", snapshot.playerName(), snapshot.playerId(), e);
                return false;
            }
        }
        return false;
    }

    private static Map<UUID, JsonObject> previousSnapshots(JsonArray previousPlayers) {
        Map<UUID, JsonObject> snapshots = new LinkedHashMap<>();
        if (previousPlayers == null) {
            return snapshots;
        }
        for (var element : previousPlayers) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            PlayerStateSnapshot snapshot = PlayerStateSnapshot.fromJson(object);
            if (snapshot != null) {
                snapshots.put(snapshot.playerId(), object.deepCopy());
            }
        }
        return snapshots;
    }

    private static boolean shouldPreservePreviousWithoutRoster(MinigameRuntime runtime) {
        GameState state = runtime.state();
        return state == GameState.PAUSED
            || state == GameState.STARTING
            || state == GameState.FROZEN
            || state == GameState.RUNNING
            || state == GameState.RUNNING
            || state == GameState.ENDING
            || state == GameState.STOPPED;
    }
}
