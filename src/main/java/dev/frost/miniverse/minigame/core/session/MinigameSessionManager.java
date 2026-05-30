package dev.frost.miniverse.minigame.core.session;

import com.google.gson.JsonObject;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.persistence.JsonSessionData;
import dev.frost.miniverse.minigame.core.persistence.SessionData;
import dev.frost.miniverse.minigame.core.persistence.SessionSerializer;
import dev.frost.miniverse.minigame.core.persistence.SessionStorage;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MinigameSessionManager {
    private static final MinigameSessionManager INSTANCE = new MinigameSessionManager();

    private final Map<String, PersistentSession> activeSessions = new LinkedHashMap<>();
    private final Map<String, SessionSerializer<? extends SessionData>> serializers = new LinkedHashMap<>();

    @Nullable
    private SessionStorage storage;

    private MinigameSessionManager() {
    }

    public static MinigameSessionManager getInstance() {
        return INSTANCE;
    }

    public synchronized void setStorage(@Nullable SessionStorage storage) {
        this.storage = storage;
    }

    public synchronized void registerSerializer(SessionSerializer<? extends SessionData> serializer) {
        this.serializers.put(normalize(serializer.gameType()), serializer);
    }

    public synchronized PersistentSession create(SessionData data, @Nullable MinigameRuntime runtime) {
        PersistentSession session = new PersistentSession(data, runtime);
        this.activeSessions.put(data.sessionId(), session);
        return session;
    }

    public synchronized PersistentSession registerActiveRuntime(MinigameRuntime runtime) {
        if (!(runtime.minigame() instanceof PersistentMinigame persistent)) {
            throw new IllegalArgumentException("Runtime minigame does not expose persistent session data.");
        }

        SessionData data = persistent.saveSessionData(runtime);
        PersistentSession session = this.activeSessions.computeIfAbsent(data.sessionId(), ignored -> new PersistentSession(data, runtime));
        session.replaceData(data);
        session.attachRuntime(runtime);
        return session;
    }

    public synchronized Optional<PersistentSession> get(String sessionId) {
        return Optional.ofNullable(this.activeSessions.get(sessionId));
    }

    public synchronized Collection<PersistentSession> activeSessions() {
        return List.copyOf(this.activeSessions.values());
    }

    public synchronized Optional<PersistentSession> unload(String sessionId) {
        return Optional.ofNullable(this.activeSessions.remove(sessionId));
    }

    public synchronized Optional<PersistentSession> restore(String sessionId) {
        if (this.storage == null) {
            return Optional.empty();
        }

        try {
            Optional<SessionData> data = this.storage.load(sessionId);
            data.ifPresent(sessionData -> this.create(sessionData, null));
            return data.map(sessionData -> this.activeSessions.get(sessionData.sessionId()));
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to restore persistent minigame session {}.", sessionId, e);
            return Optional.empty();
        }
    }

    public synchronized boolean save(String sessionId) {
        if (this.storage == null) {
            return false;
        }

        PersistentSession session = this.activeSessions.get(sessionId);
        if (session == null) {
            return false;
        }

        try {
            this.storage.save(session.data());
            return true;
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to save persistent minigame session {}.", sessionId, e);
            return false;
        }
    }

    public synchronized SessionData deserialize(JsonObject root) {
        String gameType = stringValue(root, "gameType", stringValue(root, "game", ""));
        SessionSerializer<? extends SessionData> serializer = this.serializers.get(normalize(gameType));
        if (serializer != null) {
            return serializer.deserialize(root);
        }
        return JsonSessionData.fromJson(root);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public synchronized JsonObject serialize(SessionData data) {
        SessionSerializer serializer = this.serializers.get(normalize(data.gameType()));
        return serializer == null ? data.toJson() : serializer.serialize(data);
    }

    private static String normalize(String gameType) {
        return gameType == null ? "" : gameType.trim().toLowerCase(Locale.ROOT);
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
}
