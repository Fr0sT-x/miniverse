package dev.frost.miniverse.minigame.core.persistence;

import com.google.gson.JsonObject;

public interface SessionSerializer<T extends SessionData> {
    String gameType();

    JsonObject serialize(T data);

    T deserialize(JsonObject root);
}
