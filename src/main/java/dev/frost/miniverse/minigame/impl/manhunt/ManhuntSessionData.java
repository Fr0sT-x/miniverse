package dev.frost.miniverse.minigame.impl.manhunt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.persistence.SessionData;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record ManhuntSessionData(
    String sessionId,
    GameState gameState,
    long gameTicks,
    Set<UUID> participantIds,
    Instant savedAt,
    ManhuntSettings settings,
    JsonObject runtime
) implements SessionData {
    public ManhuntSessionData {
        sessionId = sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;
        gameState = gameState == null ? GameState.WAITING_FOR_PLAYERS : gameState;
        participantIds = Set.copyOf(participantIds == null ? Set.of() : participantIds);
        savedAt = savedAt == null ? Instant.now() : savedAt;
        settings = settings == null ? ManhuntSettings.defaults() : settings;
        runtime = runtime == null ? new JsonObject() : runtime.deepCopy();
    }

    @Override
    public String gameType() {
        return ManhuntDefinition.ID;
    }

    @Override
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        root.addProperty("schema", "miniverse.session.manhunt");
        root.addProperty("sessionId", this.sessionId);
        root.addProperty("game", ManhuntDefinition.ID);
        root.addProperty("gameType", ManhuntDefinition.ID);
        root.addProperty("state", this.gameState.name());
        root.addProperty("gameState", this.gameState.name());
        root.addProperty("clockTicks", this.gameTicks);
        root.addProperty("gameTicks", this.gameTicks);
        root.addProperty("savedAt", this.savedAt.toString());
        root.add("participants", writeUuidArray(this.participantIds));
        root.add("participantIds", writeUuidArray(this.participantIds));
        root.add("settings", writeSettings(this.settings));
        root.add("runtime", this.runtime.deepCopy());
        return root;
    }

    static ManhuntSessionData fromJson(JsonObject root) {
        JsonObject runtime = root.has("runtime") && root.get("runtime").isJsonObject()
            ? root.getAsJsonObject("runtime")
            : root.deepCopy();
        return new ManhuntSessionData(
            stringValue(root, "sessionId", UUID.randomUUID().toString()),
            parseState(stringValue(root, "gameState", stringValue(root, "state", GameState.WAITING_FOR_PLAYERS.name()))),
            longValue(root, "gameTicks", longValue(root, "clockTicks", 0L)),
            readUuidArray(root.has("participantIds") ? root.getAsJsonArray("participantIds") : root.getAsJsonArray("participants")),
            parseInstant(stringValue(root, "savedAt", Instant.now().toString())),
            readSettings(root.has("settings") && root.get("settings").isJsonObject() ? root.getAsJsonObject("settings") : null),
            runtime
        );
    }

    private static JsonObject writeSettings(ManhuntSettings settings) {
        JsonObject object = new JsonObject();
        object.addProperty("hunterReleaseDelaySeconds", settings.hunterReleaseDelaySeconds());
        object.addProperty("runnerRespawnDelaySeconds", settings.runnerRespawnDelaySeconds());
        object.addProperty("huntersCompassEnabled", settings.huntersCompassEnabled());
        object.addProperty("netherTrackingEnabled", settings.netherTrackingEnabled());
        object.addProperty("compassCooldownSeconds", settings.compassCooldownSeconds());
        object.addProperty("runnerGlowPulseMinutes", settings.runnerGlowPulseMinutes());
        object.addProperty("runnerLives", settings.runnerLives());
        object.addProperty("hunterLives", settings.hunterLives());
        object.addProperty("hunterRespawnDelaySeconds", settings.hunterRespawnDelaySeconds());
        object.addProperty("midGameJoinTeleportEnabled", settings.midGameJoinTeleportEnabled());
        object.addProperty("disconnectGraceSeconds", settings.disconnectGraceSeconds());
        object.addProperty("runnerRespawnAtTeammate", settings.runnerRespawnAtTeammate());
        object.addProperty("hunterRespawnAtTeammate", settings.hunterRespawnAtTeammate());
        return object;
    }

    private static ManhuntSettings readSettings(JsonObject object) {
        ManhuntSettings defaults = ManhuntSettings.defaults();
        if (object == null) {
            return defaults;
        }
        
        int runnerRespawnDelay = intValue(object, "runnerRespawnDelaySeconds", -1);
        if (runnerRespawnDelay == -1) {
            runnerRespawnDelay = intValue(object, "speedrunnerRespawnDelaySeconds", defaults.runnerRespawnDelaySeconds());
        }

        return new ManhuntSettings(
            intValue(object, "hunterReleaseDelaySeconds", defaults.hunterReleaseDelaySeconds()),
            runnerRespawnDelay,
            booleanValue(object, "huntersCompassEnabled", defaults.huntersCompassEnabled()),
            booleanValue(object, "netherTrackingEnabled", defaults.netherTrackingEnabled()),
            intValue(object, "compassCooldownSeconds", defaults.compassCooldownSeconds()),
            intValue(object, "runnerGlowPulseMinutes", defaults.runnerGlowPulseMinutes()),
            intValue(object, "runnerLives", defaults.runnerLives()),
            intValue(object, "hunterLives", defaults.hunterLives()),
            intValue(object, "hunterRespawnDelaySeconds", defaults.hunterRespawnDelaySeconds()),
            booleanValue(object, "midGameJoinTeleportEnabled", defaults.midGameJoinTeleportEnabled()),
            intValue(object, "disconnectGraceSeconds", defaults.disconnectGraceSeconds()),
            booleanValue(object, "runnerRespawnAtTeammate", defaults.runnerRespawnAtTeammate()),
            booleanValue(object, "hunterRespawnAtTeammate", defaults.hunterRespawnAtTeammate())
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

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
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
