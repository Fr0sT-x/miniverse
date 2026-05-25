package dev.frost.miniverse.minigame.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.session.SessionRuntimeConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

public final class MinigameSessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "miniverse-game-session.json";

    private MinigameSessionStore() {
    }

    public static boolean saveActiveRuntime() {
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        return runtime != null && save(runtime);
    }

    public static boolean save(MinigameRuntime runtime) {
        if (runtime == null || !(runtime.minigame() instanceof PersistentMinigame persistent)) {
            return false;
        }

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("savedAt", Instant.now().toString());
        root.addProperty("game", runtime.minigame().getName());
        SessionRuntimeConfig.getSessionId().ifPresent(sessionId -> root.addProperty("sessionId", sessionId));
        root.addProperty("state", runtime.state().name());
        root.addProperty("clockTicks", runtime.context().clock().ticks());
        root.add("runtime", persistent.saveRuntimeState());

        Path path = savePath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
            return true;
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to save minigame session state to {}.", path, e);
            return false;
        }
    }

    public static boolean loadInto(MinigameRuntime runtime) {
        if (runtime == null || !(runtime.minigame() instanceof PersistentMinigame persistent)) {
            return false;
        }

        Optional<JsonObject> saved = read();
        if (saved.isEmpty()) {
            return false;
        }

        JsonObject root = saved.get();
        if (!root.has("runtime") || !root.get("runtime").isJsonObject()) {
            return false;
        }

        persistent.loadRuntimeState(root.getAsJsonObject("runtime"));
        return true;
    }

    public static Optional<JsonObject> read() {
        Path path = savePath();
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            Miniverse.LOGGER.error("Failed to read minigame session state from {}.", path, e);
            return Optional.empty();
        }
    }

    public static Path savePath() {
        String configPath = System.getProperty("miniverse.session.config", "");
        if (!configPath.isBlank()) {
            Path config = Paths.get(configPath).toAbsolutePath().normalize();
            Path parent = config.getParent();
            if (parent != null) {
                return parent.resolve(FILE_NAME);
            }
        }
        return Paths.get("").toAbsolutePath().normalize().resolve(FILE_NAME);
    }
}
