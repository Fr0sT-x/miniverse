package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.common.MiniverseFileUtils;
import dev.frost.miniverse.common.MiniversePaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class SessionRegistry {
    private static final String SESSION_JSON_FILE_NAME = "session.json";
    private static final String LEGACY_SESSION_FILE_NAME = "session.properties";

    private SessionRegistry() {
    }

    public record Snapshot(String sessionId, String game, String state, long seed, int playerCount, List<String> players) {
    }

    public record StopState(String sessionId, boolean stopRequested, boolean returnComplete, boolean seedChangeRequested) {
    }

    public record SeedChangeTarget(String host, int port) {
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
                    boolean seedChangeRequested = SessionConfigJson.lifecycleFlag(object, "seedChangeRequested");
                    if (stopRequested || returnComplete || seedChangeRequested) {
                        stopStates.add(new StopState(sessionId, stopRequested, returnComplete, seedChangeRequested));
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

    public static synchronized void markSeedChangeRequested(String sessionId) {
        updateSeedChangeRequested(sessionId, true);
    }

    public static synchronized void clearSeedChangeRequested(String sessionId) {
        updateSeedChangeRequested(sessionId, false);
    }

    public static synchronized boolean isSeedChangeRequested(String sessionId) {
        return readJson(sessionId).map(json -> SessionConfigJson.lifecycleFlag(json, "seedChangeRequested")).orElse(false);
    }

    public static synchronized void setSeedChangeTarget(String sessionId, String groupLabel, String host, int port) {
        if (groupLabel == null || groupLabel.isBlank() || host == null || host.isBlank() || port <= 0) {
            return;
        }
        updateLifecycleObject(sessionId, lifecycle -> {
            JsonObject targets = lifecycle.has("seedChangeTargets") && lifecycle.get("seedChangeTargets").isJsonObject()
                ? lifecycle.getAsJsonObject("seedChangeTargets")
                : new JsonObject();
            JsonObject target = new JsonObject();
            target.addProperty("host", host);
            target.addProperty("port", port);
            targets.add(groupLabel, target);
            lifecycle.add("seedChangeTargets", targets);
        });
    }

    public static synchronized Optional<SeedChangeTarget> getSeedChangeTarget(String sessionId, String groupLabel) {
        if (groupLabel == null || groupLabel.isBlank()) {
            return Optional.empty();
        }
        return readJson(sessionId).flatMap(json -> {
            JsonObject lifecycle = lifecycleObject(json);
            if (!lifecycle.has("seedChangeTargets") || !lifecycle.get("seedChangeTargets").isJsonObject()) {
                return Optional.empty();
            }
            JsonObject targets = lifecycle.getAsJsonObject("seedChangeTargets");
            if (!targets.has(groupLabel) || !targets.get(groupLabel).isJsonObject()) {
                return Optional.empty();
            }
            JsonObject target = targets.getAsJsonObject(groupLabel);
            String host = SessionConfigJson.string(target, "host", "127.0.0.1");
            int port = SessionConfigJson.integer(target, "port", 0);
            return port > 0 ? Optional.of(new SeedChangeTarget(host, port)) : Optional.empty();
        });
    }

    public static synchronized void clearSeedChangeTargets(String sessionId) {
        updateLifecycleObject(sessionId, lifecycle -> {
            lifecycle.remove("seedChangeTargets");
            lifecycle.remove("seedChangeCompletedGroups");
        });
    }

    public static synchronized void markSeedChangeTransferComplete(String sessionId, String groupLabel) {
        if (groupLabel == null || groupLabel.isBlank()) {
            return;
        }
        updateLifecycleObject(sessionId, lifecycle -> {
            Set<String> completed = seedChangeCompletedGroups(lifecycle);
            completed.add(groupLabel);
            JsonArray array = new JsonArray();
            completed.forEach(array::add);
            lifecycle.add("seedChangeCompletedGroups", array);
        });
    }

    public static synchronized boolean areSeedChangeTransfersComplete(String sessionId, Collection<String> expectedGroupLabels) {
        if (expectedGroupLabels == null || expectedGroupLabels.isEmpty()) {
            return false;
        }
        Optional<JsonObject> json = readJson(sessionId);
        if (json.isEmpty()) {
            return false;
        }
        Set<String> completed = seedChangeCompletedGroups(lifecycleObject(json.get()));
        return completed.containsAll(expectedGroupLabels);
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
        boolean seedChangeRequested = isSeedChangeRequested(session.getSessionId());
        JsonObject json = SessionConfigJson.baseSession(session);
        json.addProperty("playerCount", session.getGroups().stream().mapToInt(SessionGroup::getPlayerCount).sum());
        json.addProperty("groupCount", session.getGroups().size());
        json.addProperty("assignmentCount", session.getGroups().size());
        JsonObject lifecycle = SessionConfigJson.lifecycle(stopRequested, returnComplete, seedChangeRequested);
        readJson(session.getSessionId())
            .map(SessionRegistry::lifecycleObject)
            .ifPresent(existing -> copySeedChangeHandoff(existing, lifecycle));
        json.add("lifecycle", lifecycle);
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
            Boolean.parseBoolean(properties.getProperty("returnComplete", "false")),
            Boolean.parseBoolean(properties.getProperty("seedChangeRequested", "false"))
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
            boolean seedChangeRequested = SessionConfigJson.lifecycleFlag(json, "seedChangeRequested");
            JsonObject lifecycle = SessionConfigJson.lifecycle(requested, returnComplete, seedChangeRequested);
            copySeedChangeHandoff(lifecycleObject(json), lifecycle);
            json.add("lifecycle", lifecycle);
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
            boolean seedChangeRequested = SessionConfigJson.lifecycleFlag(json, "seedChangeRequested");
            JsonObject lifecycle = SessionConfigJson.lifecycle(stopRequested, returnComplete, seedChangeRequested);
            copySeedChangeHandoff(lifecycleObject(json), lifecycle);
            json.add("lifecycle", lifecycle);
            SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update session return flag for {}", sessionId, e);
        }
    }

    private static void updateSeedChangeRequested(String sessionId, boolean seedChangeRequested) {
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        if (!Files.exists(sessionRoot)) {
            return;
        }

        try {
            Files.createDirectories(sessionRoot);
            JsonObject json = readJson(sessionId).orElseGet(JsonObject::new);
            json.addProperty("sessionId", sessionId);
            boolean stopRequested = SessionConfigJson.lifecycleFlag(json, "stopRequested");
            boolean returnComplete = SessionConfigJson.lifecycleFlag(json, "returnComplete");
            JsonObject lifecycle = SessionConfigJson.lifecycle(stopRequested, returnComplete, seedChangeRequested);
            copySeedChangeHandoff(lifecycleObject(json), lifecycle);
            json.add("lifecycle", lifecycle);
            SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update session seed change flag for {}", sessionId, e);
        }
    }

    private static Path sessionsRoot() {
        if (SessionRuntimeConfig.isSessionServer()) {
            return SessionRuntimeConfig.getMainSessionsRoot().orElseGet(MiniversePaths::sessionsRoot);
        }
        return MiniversePaths.sessionsRoot();
    }

    private static void updateLifecycleObject(String sessionId, java.util.function.Consumer<JsonObject> updater) {
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        if (!Files.exists(sessionRoot)) {
            return;
        }

        try {
            Files.createDirectories(sessionRoot);
            JsonObject json = readJson(sessionId).orElseGet(JsonObject::new);
            json.addProperty("sessionId", sessionId);
            JsonObject lifecycle = lifecycleObject(json);
            updater.accept(lifecycle);
            json.add("lifecycle", lifecycle);
            SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update session lifecycle for {}", sessionId, e);
        }
    }

    private static JsonObject lifecycleObject(JsonObject json) {
        return json.has("lifecycle") && json.get("lifecycle").isJsonObject()
            ? json.getAsJsonObject("lifecycle")
            : json;
    }

    private static Set<String> seedChangeCompletedGroups(JsonObject lifecycle) {
        Set<String> completed = new LinkedHashSet<>();
        if (!lifecycle.has("seedChangeCompletedGroups") || !lifecycle.get("seedChangeCompletedGroups").isJsonArray()) {
            return completed;
        }
        for (var element : lifecycle.getAsJsonArray("seedChangeCompletedGroups")) {
            if (!element.isJsonNull()) {
                completed.add(element.getAsString());
            }
        }
        return completed;
    }

    private static void copySeedChangeHandoff(JsonObject sourceLifecycle, JsonObject targetLifecycle) {
        if (sourceLifecycle.has("seedChangeTargets")) {
            targetLifecycle.add("seedChangeTargets", sourceLifecycle.get("seedChangeTargets").deepCopy());
        }
        if (sourceLifecycle.has("seedChangeCompletedGroups")) {
            targetLifecycle.add("seedChangeCompletedGroups", sourceLifecycle.get("seedChangeCompletedGroups").deepCopy());
        }
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
        try {
            MiniverseFileUtils.deleteRecursively(root);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to delete session registry root {}", root, e);
        }
    }
}
