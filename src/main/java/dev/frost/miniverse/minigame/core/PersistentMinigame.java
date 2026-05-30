package dev.frost.miniverse.minigame.core;

import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.persistence.JsonSessionData;
import dev.frost.miniverse.minigame.core.persistence.SessionData;
import dev.frost.miniverse.session.SessionRuntimeConfig;

import java.time.Instant;
import java.util.UUID;

/**
 * Optional contract for minigames whose runtime state can be saved and loaded
 * independently of the Minecraft world/playerdata files.
 */
public interface PersistentMinigame {
    JsonObject saveRuntimeState();

    void loadRuntimeState(JsonObject state);

    default SessionData saveSessionData(MinigameRuntime runtime) {
        String sessionId = SessionRuntimeConfig.getSessionId().orElseGet(() -> UUID.randomUUID().toString());
        return new JsonSessionData(
            sessionId,
            runtime.minigame().getName(),
            runtime.state(),
            runtime.context().clock().ticks(),
            runtime.context().participantIds(),
            Instant.now(),
            this.saveRuntimeState()
        );
    }

    default void loadSessionData(SessionData data) {
        if (data instanceof JsonSessionData jsonSessionData) {
            this.loadRuntimeState(jsonSessionData.payload());
            return;
        }
        JsonObject root = data.toJson();
        if (root.has("runtime") && root.get("runtime").isJsonObject()) {
            this.loadRuntimeState(root.getAsJsonObject("runtime"));
        } else {
            this.loadRuntimeState(root);
        }
    }
}
