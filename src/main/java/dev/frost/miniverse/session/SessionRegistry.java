package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.common.MiniversePaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public final class SessionRegistry {
    private static final String SESSION_JSON_FILE_NAME = "session.json";
    private static final String LEGACY_SESSION_FILE_NAME = "session.properties";

    private SessionRegistry() {
    }

    public record Snapshot(String sessionId, String game, String state, long seed, int playerCount, List<String> players) {
    }

    public record StopState(String sessionId, boolean stopRequested, boolean returnComplete) {
    }

    public static synchronized void writeSnapshot(Collection<GameSession> sessions) {
        Path sessionsRoot = sessionsRoot();
        try {
            Files.createDirectories(sessionsRoot);
            for (GameSession session : sessions) {
                writeSessionFile(sessionsRoot, session);
            }
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to persist session registry snapshot", e);
        }
    }

    public static synchronized List<Snapshot> loadSnapshots() {
        Path sessionsRoot = sessionsRoot();
        if (!Files.exists(sessionsRoot)) {
            return List.of();
        }

        List<Snapshot> snapshots = new ArrayList<>();
        try (var stream = Files.list(sessionsRoot)) {
            stream.filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .map(SessionRegistry::loadSnapshot)
                .flatMap(Optional::stream)
                .forEach(snapshots::add);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to read session registry snapshot", e);
        }
        return snapshots;
    }

    public static synchronized List<StopState> loadStopStates() {
        Path sessionsRoot = sessionsRoot();
        if (!Files.exists(sessionsRoot)) {
            return List.of();
        }

        List<StopState> stopStates = new ArrayList<>();
        try (var stream = Files.list(sessionsRoot)) {
            stream.filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(path -> {
                    String sessionId = path.getFileName().toString();
                    Optional<JsonObject> json = readJson(sessionId);
                    if (json.isEmpty()) {
                        return;
                    }
                    JsonObject object = json.get();
                    boolean stopRequested = SessionConfigJson.lifecycleFlag(object, "stopRequested");
                    boolean returnComplete = SessionConfigJson.lifecycleFlag(object, "returnComplete");
                    if (stopRequested || returnComplete) {
                        stopStates.add(new StopState(sessionId, stopRequested, returnComplete));
                    }
                });
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to read session stop states", e);
        }
        return stopStates;
    }

    public static synchronized void markStopRequested(String sessionId) {
        updateStopRequested(sessionId, true);
    }

    public static synchronized void clearStopRequested(String sessionId) {
        updateStopRequested(sessionId, false);
    }

    public static synchronized boolean isStopRequested(String sessionId) {
        return readJson(sessionId).map(json -> SessionConfigJson.lifecycleFlag(json, "stopRequested")).orElse(false);
    }


    public static synchronized void markReturnComplete(String sessionId) {
        updateReturnComplete(sessionId, true);
    }

    public static synchronized void clearReturnComplete(String sessionId) {
        updateReturnComplete(sessionId, false);
    }

    public static synchronized boolean isReturnComplete(String sessionId) {
        return readJson(sessionId).map(json -> SessionConfigJson.lifecycleFlag(json, "returnComplete")).orElse(false);
    }


    public static synchronized void removeSession(String sessionId) {
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        deleteRecursively(sessionRoot);
    }

    public static synchronized void cleanupSessionsOnStartup() {
        if (SessionRuntimeConfig.isSessionServer()) {
            return;
        }

        Path sessionsRoot = sessionsRoot();
        try {
            Files.createDirectories(sessionsRoot);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to create sessions root {}", sessionsRoot, e);
            return;
        }

        List<Path> entries;
        try (var stream = Files.list(sessionsRoot)) {
            entries = stream.toList();
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to scan sessions root {}", sessionsRoot, e);
            return;
        }

        if (entries.isEmpty()) {
            Miniverse.LOGGER.info("Session startup cleanup: no stale session folders found.");
            return;
        }

        int removed = 0;
        for (Path entry : entries) {
            try {
                if (Files.isDirectory(entry)) {
                    deleteRecursively(entry);
                } else {
                    Files.deleteIfExists(entry);
                }
                removed++;
                Miniverse.LOGGER.info("Session startup cleanup: removed {}", entry.getFileName());
            } catch (IOException e) {
                Miniverse.LOGGER.warn("Session startup cleanup: failed to remove {}", entry, e);
            }
        }

        Miniverse.LOGGER.info("Session startup cleanup complete: removed {} item(s).", removed);
    }

    private static void writeSessionFile(Path sessionsRoot, GameSession session) throws IOException {
        Path sessionRoot = sessionsRoot.resolve(session.getSessionId());
        Files.createDirectories(sessionRoot);

        boolean stopRequested = isStopRequested(session.getSessionId());
        boolean returnComplete = isReturnComplete(session.getSessionId());
        JsonObject json = SessionConfigJson.baseSession(session);
        json.addProperty("playerCount", session.getGroups().stream().mapToInt(SessionGroup::getPlayerCount).sum());
        json.addProperty("groupCount", session.getGroups().size());
        json.addProperty("assignmentCount", session.getGroups().size());
        json.add("lifecycle", SessionConfigJson.lifecycle(stopRequested, returnComplete));
        SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
    }

    private static Optional<Snapshot> loadSnapshot(Path sessionRoot) {
        Path file = sessionRoot.resolve(SESSION_JSON_FILE_NAME);
        if (Files.isRegularFile(file)) {
            return loadJsonSnapshot(sessionRoot, file);
        }

        file = sessionRoot.resolve(LEGACY_SESSION_FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to load session snapshot {}", file, e);
            return Optional.empty();
        }

        String sessionId = properties.getProperty("sessionId", sessionRoot.getFileName().toString());
        String game = properties.getProperty("game", sessionId);
        String state = properties.getProperty("state", SessionState.CREATED.name());
        long seed = parseLong(properties.getProperty("seed", "0"), 0L);
        int playerCount = parseInt(properties.getProperty("playerCount", "0"), 0);

        int assignmentCount = parseInt(properties.getProperty("groupCount", properties.getProperty("assignmentCount", "0")), 0);
        List<String> players = new ArrayList<>();
        for (int i = 0; i < assignmentCount; i++) {
            String displayName = properties.getProperty("group." + i + ".displayName", properties.getProperty("assignment." + i + ".displayName", ""));
            if (!displayName.isBlank()) {
                players.add(displayName);
            }
        }

        return Optional.of(new Snapshot(sessionId, game, state, seed, playerCount, players));
    }

    private static Optional<Snapshot> loadJsonSnapshot(Path sessionRoot, Path file) {
        Optional<JsonObject> json = SessionConfigJson.read(file);
        if (json.isEmpty()) {
            return Optional.empty();
        }

        JsonObject object = json.get();
        String sessionId = SessionConfigJson.string(object, "sessionId", sessionRoot.getFileName().toString());
        String game = SessionConfigJson.string(object, "gameId", sessionId);
        String state = SessionConfigJson.string(object, "state", SessionState.CREATED.name());
        long seed = SessionConfigJson.longValue(object, "seed", 0L);
        int playerCount = SessionConfigJson.integer(object, "playerCount", 0);

        List<String> players = new ArrayList<>();
        JsonArray assignments = object.has("backendAssignments") && object.get("backendAssignments").isJsonArray()
            ? object.getAsJsonArray("backendAssignments")
            : object.has("teams") && object.get("teams").isJsonArray() ? object.getAsJsonArray("teams") : new JsonArray();
        for (var assignmentElement : assignments) {
            if (!assignmentElement.isJsonObject()) {
                continue;
            }
            String displayName = SessionConfigJson.string(assignmentElement.getAsJsonObject(), "displayName", "");
            if (!displayName.isBlank()) {
                players.add(displayName);
            }
        }

        return Optional.of(new Snapshot(sessionId, game, state, seed, playerCount, players));
    }

    private static Optional<JsonObject> readJson(String sessionId) {
        Path file = sessionsRoot().resolve(sessionId).resolve(SESSION_JSON_FILE_NAME);
        if (Files.isRegularFile(file)) {
            return SessionConfigJson.read(file);
        }

        return readLegacyProperties(sessionId).map(SessionRegistry::legacyPropertiesToJson);
    }

    private static Optional<Properties> readLegacyProperties(String sessionId) {
        Path file = sessionsRoot().resolve(sessionId).resolve(LEGACY_SESSION_FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            return Optional.of(properties);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to read session snapshot {}", file, e);
            return Optional.empty();
        }
    }

    private static JsonObject legacyPropertiesToJson(Properties properties) {
        JsonObject json = new JsonObject();
        json.addProperty("version", 0);
        json.addProperty("sessionId", properties.getProperty("sessionId", ""));
        json.addProperty("gameId", properties.getProperty("game", ""));
        json.addProperty("state", properties.getProperty("state", SessionState.CREATED.name()));
        json.addProperty("seed", parseLong(properties.getProperty("seed", "0"), 0L));
        json.addProperty("playerCount", parseInt(properties.getProperty("playerCount", "0"), 0));
        json.add("lifecycle", SessionConfigJson.lifecycle(
            Boolean.parseBoolean(properties.getProperty("stopRequested", "false")),
            Boolean.parseBoolean(properties.getProperty("returnComplete", "false"))
        ));

        int assignmentCount = parseInt(properties.getProperty("groupCount", properties.getProperty("assignmentCount", "0")), 0);
        JsonArray assignments = new JsonArray();
        for (int i = 0; i < assignmentCount; i++) {
            JsonObject assignment = new JsonObject();
            assignment.addProperty("label", properties.getProperty("group." + i + ".label", properties.getProperty("assignment." + i + ".label", "")));
            assignment.addProperty("displayName", properties.getProperty("group." + i + ".displayName", properties.getProperty("assignment." + i + ".displayName", "")));
            assignment.addProperty("state", properties.getProperty("group." + i + ".state", properties.getProperty("assignment." + i + ".state", SessionState.CREATED.name())));
            assignment.addProperty("playerCount", parseInt(properties.getProperty("group." + i + ".playerCount", properties.getProperty("assignment." + i + ".playerCount", "0")), 0));
            assignments.add(assignment);
        }
        json.add("backendAssignments", assignments);
        return json;
    }


    private static void updateStopRequested(String sessionId, boolean requested) {
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        if (!Files.exists(sessionRoot)) {
            return;
        }

        try {
            Files.createDirectories(sessionRoot);
            JsonObject json = readJson(sessionId).orElseGet(JsonObject::new);
            json.addProperty("sessionId", sessionId);
            boolean returnComplete = SessionConfigJson.lifecycleFlag(json, "returnComplete");
            json.add("lifecycle", SessionConfigJson.lifecycle(requested, returnComplete));
            SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update session stop flag for {}", sessionId, e);
        }
    }

    private static void updateReturnComplete(String sessionId, boolean returnComplete) {
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        if (!Files.exists(sessionRoot)) {
            return;
        }

        try {
            Files.createDirectories(sessionRoot);
            JsonObject json = readJson(sessionId).orElseGet(JsonObject::new);
            json.addProperty("sessionId", sessionId);
            boolean stopRequested = SessionConfigJson.lifecycleFlag(json, "stopRequested");
            json.add("lifecycle", SessionConfigJson.lifecycle(stopRequested, returnComplete));
            SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update session return flag for {}", sessionId, e);
        }
    }

    private static Path sessionsRoot() {
        return MiniversePaths.sessionsRoot();
    }


    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }

        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    Miniverse.LOGGER.warn("Failed to delete session registry path {}", path, e);
                }
            });
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to delete session registry root {}", root, e);
        }
    }
}
