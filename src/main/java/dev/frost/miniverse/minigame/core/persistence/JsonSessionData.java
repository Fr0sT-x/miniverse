package dev.frost.miniverse.minigame.core.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.GameState;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record JsonSessionData(
    String sessionId,
    String gameType,
    GameState gameState,
    long gameTicks,
    Set<UUID> participantIds,
    Instant savedAt,
    JsonObject payload
) implements SessionData {
    public JsonSessionData {
        sessionId = sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;
        gameType = gameType == null || gameType.isBlank() ? "unknown" : gameType;
        gameState = gameState == null ? GameState.WAITING_FOR_PLAYERS : gameState;
        participantIds = Set.copyOf(participantIds == null ? Set.of() : participantIds);
        savedAt = savedAt == null ? Instant.now() : savedAt;
        payload = payload == null ? new JsonObject() : payload.deepCopy();
    }

    @Override
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("sessionId", this.sessionId);
        root.addProperty("game", this.gameType);
        root.addProperty("gameType", this.gameType);
        root.addProperty("state", this.gameState.name());
        root.addProperty("gameState", this.gameState.name());
        root.addProperty("clockTicks", this.gameTicks);
        root.addProperty("gameTicks", this.gameTicks);
        root.addProperty("savedAt", this.savedAt.toString());
        root.add("participants", writeUuidArray(this.participantIds));
        root.add("participantIds", writeUuidArray(this.participantIds));
        root.add("runtime", this.payload.deepCopy());
        return root;
    }

    public static JsonSessionData fromJson(JsonObject root) {
        JsonObject payload = root.has("runtime") && root.get("runtime").isJsonObject()
            ? root.getAsJsonObject("runtime")
            : root;
        return new JsonSessionData(
            stringValue(root, "sessionId", UUID.randomUUID().toString()),
            stringValue(root, "gameType", stringValue(root, "game", "unknown")),
            parseState(stringValue(root, "gameState", stringValue(root, "state", GameState.WAITING_FOR_PLAYERS.name()))),
            longValue(root, "gameTicks", longValue(root, "clockTicks", 0L)),
            readUuidArray(root.has("participantIds") ? root.getAsJsonArray("participantIds") : root.getAsJsonArray("participants")),
            parseInstant(stringValue(root, "savedAt", Instant.now().toString())),
            payload
        );
    }

    private static JsonArray writeUuidArray(Set<UUID> uuids) {
        JsonArray array = new JsonArray();
        for (UUID uuid : uuids) {
            array.add(uuid.toString());
        }
        return array;
    }

    private static Set<UUID> readUuidArray(JsonArray array) {
        Set<UUID> uuids = new LinkedHashSet<>();
        if (array == null) {
            return uuids;
        }
        for (var element : array) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            try {
                uuids.add(UUID.fromString(element.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return uuids;
    }

    private static GameState parseState(String value) {
        try {
            return GameState.valueOf(value);
        } catch (RuntimeException ignored) {
            return GameState.WAITING_FOR_PLAYERS;
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.now();
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsLong() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
