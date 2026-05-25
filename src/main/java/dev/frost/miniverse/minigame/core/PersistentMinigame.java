package dev.frost.miniverse.minigame.core;

import com.google.gson.JsonObject;

/**
 * Optional contract for minigames whose runtime state can be saved and loaded
 * independently of the Minecraft world/playerdata files.
 */
public interface PersistentMinigame {
    JsonObject saveRuntimeState();

    void loadRuntimeState(JsonObject state);
}
