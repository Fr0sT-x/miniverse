package dev.frost.miniverse.minigame.core.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.minigame.core.session.MinigameSessionManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Filesystem storage for future multi-session persistence.
 * Current backend session snapshots still use MinigameSessionStore's legacy file path.
 */
public final class FileSessionStorage implements SessionStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "session.json";

    private final Path root;

    public FileSessionStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void save(SessionData data) throws IOException {
        Path file = this.fileFor(data.sessionId());
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(data.toJson(), writer);
        }
    }

    @Override
    public Optional<SessionData> load(String sessionId) throws IOException {
        Path file = this.fileFor(sessionId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(this.read(file));
    }

    @Override
    public List<SessionData> loadAll() throws IOException {
        if (!Files.exists(this.root)) {
            return List.of();
        }

        List<SessionData> sessions = new ArrayList<>();
        try (var stream = Files.walk(this.root, 3)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().equals(FILE_NAME)).toList()) {
                sessions.add(this.read(file));
            }
        }
        return sessions;
    }

    @Override
    public void delete(String sessionId) throws IOException {
        Files.deleteIfExists(this.fileFor(sessionId));
    }

    private SessionData read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return MinigameSessionManager.getInstance().deserialize(root);
        }
    }

    private Path fileFor(String sessionId) {
        return this.root.resolve(sessionId).resolve(FILE_NAME);
    }
}
