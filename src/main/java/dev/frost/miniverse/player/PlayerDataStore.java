package dev.frost.miniverse.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public final class PlayerDataStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PlayerDataStore() {}

    private static Path getProfilePath(UUID playerId) {
        return MiniversePaths.profilesRoot().resolve(playerId.toString() + ".json");
    }

    public static JsonObject getProfile(UUID playerId) {
        Path path = getProfilePath(playerId);
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            var parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (IOException | IllegalStateException e) {
            Miniverse.LOGGER.warn("Failed to read player profile for {}", playerId, e);
        }
        return new JsonObject();
    }

    public static void saveProfile(UUID playerId, JsonObject profile) {
        Path path = getProfilePath(playerId);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(profile, writer);
            }
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to write player profile for {}", playerId, e);
        }
    }
}
