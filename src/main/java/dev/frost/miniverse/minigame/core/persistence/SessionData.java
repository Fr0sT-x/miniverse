package dev.frost.miniverse.minigame.core.persistence;

import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.GameState;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Serializable state owned by a minigame session.
 * Implementations must not retain live server, entity, listener, or packet references.
 */
public interface SessionData {
    String sessionId();

    String gameType();

    GameState gameState();

    long gameTicks();

    Set<UUID> participantIds();

    Instant savedAt();

    JsonObject toJson();
}
