package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.common.MiniverseFileUtils;
import dev.frost.miniverse.common.MiniversePaths;
import dev.frost.miniverse.minigame.core.MinigameSessionStore;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class SessionRegistry {
    private static final String SESSION_JSON_FILE_NAME = "session.json";
    private static final String LEGACY_SESSION_FILE_NAME = "session.properties";

    private static List<Snapshot> cachedSnapshots = null;

    private SessionRegistry() {
    }

    public record Snapshot(String sessionId, String game, String state, long seed, int playerCount, List<String> players, long createdAtMillis, long launchedAtMillis, long updatedAtMillis, long playedMillis, boolean inspectable) {
    }

    public record RetainedSession(String sessionId, String gameId, SeedPlan seedPlan, NbtCompound settings, List<PlannedTeam> teams) {
    }

    private record CleanupCandidate(Path path, long updatedAtMillis) {
    }
    public record StopState(String sessionId, boolean stopRequested, boolean returnComplete, boolean seedChangeRequested) {
    }

    private record SaveMetadata(long clockTicks, long savedAtMillis, long lastModifiedMillis) {
    }

    public record SeedChangeTarget(String host, int port) {
    }

    public record PendingJoinNotice(UUID playerId, String playerName, long joinedAtMillis) {
    }

    public record MidGameAssignmentRequest(String requestId, String sessionId, UUID playerId, String teamLabel, String role, String requestedBy, long requestedAtMillis) {
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
        if (cachedSnapshots != null) {
            return cachedSnapshots;
        }

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
        cachedSnapshots = snapshots;
        return snapshots;
    }

    public static synchronized Optional<Snapshot> loadSnapshot(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        if (!Files.isDirectory(sessionRoot)) {
            return Optional.empty();
        }
        return loadSnapshot(sessionRoot);
    }

    public static synchronized Properties loadRuntimeProperties(String sessionId) {
        Properties properties = new Properties();
        if (sessionId == null || sessionId.isBlank()) {
            return properties;
        }
        readJson(sessionId).ifPresent(json -> SessionConfigJson.flattenRuntimeJson(json, properties));
        return properties;
    }

    public static synchronized Optional<RetainedSession> loadRetainedSession(String sessionId) {
        Optional<JsonObject> json = readJson(sessionId);
        if (json.isEmpty()) {
            return Optional.empty();
        }

        JsonObject object = json.get();
        String gameId = SessionConfigJson.string(object, "gameId", SessionConfigJson.string(object, "game", ""));
        if (gameId.isBlank()) {
            return Optional.empty();
        }

        long seed = SessionConfigJson.longValue(object, "seed", 0L);
        SeedPlan seedPlan = SeedPlan.fixed(seed);
        NbtCompound settings = readSettingsNbt(object, sessionId);
        List<PlannedTeam> teams = readTeams(object, sessionId);
        return Optional.of(new RetainedSession(sessionId, gameId, seedPlan, settings, teams));
    }

    public static synchronized Set<String> existingSessionIds() {
        Path sessionsRoot = sessionsRoot();
        if (!Files.exists(sessionsRoot)) {
            return Set.of();
        }

        Set<String> ids = new HashSet<>();
        try (var stream = Files.list(sessionsRoot)) {
            stream.filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .forEach(ids::add);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to scan existing session ids", e);
        }
        return ids;
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

    public static synchronized void markPauseRequested(String sessionId) {
        updatePauseRequested(sessionId, true);
    }

    public static synchronized void clearPauseRequested(String sessionId) {
        updatePauseRequested(sessionId, false);
    }

    public static synchronized boolean isPauseRequested(String sessionId) {
        return readJson(sessionId).map(json -> SessionConfigJson.lifecycleFlag(json, "pauseRequested")).orElse(false);
    }

    public static synchronized void recordPendingJoinNotice(Collection<String> sessionIds, UUID playerId, String playerName, long joinedAtMillis) {
        if (sessionIds == null || sessionIds.isEmpty() || playerId == null) {
            return;
        }
        for (String sessionId : sessionIds) {
            recordPendingJoinNotice(sessionId, playerId, playerName, joinedAtMillis);
        }
    }

    public static synchronized void removePendingJoinNotice(UUID playerId) {
        if (playerId == null) {
            return;
        }

        Path sessionsRoot = sessionsRoot();
        if (!Files.exists(sessionsRoot)) {
            return;
        }

        try (var stream = Files.list(sessionsRoot)) {
            stream.filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .forEach(sessionId -> updateLifecycleObject(sessionId, lifecycle -> removePendingJoinNotice(lifecycle, playerId)));
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to remove pending late-join notice for {}", playerId, e);
        }
    }

    public static synchronized List<PendingJoinNotice> listPendingJoinNotices(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        Optional<JsonObject> json = readJson(sessionId);
        if (json.isEmpty()) {
            return List.of();
        }
        return pendingJoinNotices(lifecycleObject(json.get()));
    }

    public static synchronized void addMidGameAssignmentRequest(String sessionId, UUID playerId, String teamLabel, String role, String requestedBy) {
        if (sessionId == null || sessionId.isBlank() || playerId == null) {
            return;
        }
        String requestId = playerId + "-" + System.currentTimeMillis();
        updateLifecycleObject(sessionId, lifecycle -> {
            JsonArray requests = lifecycle.has("midGameAssignmentRequests") && lifecycle.get("midGameAssignmentRequests").isJsonArray()
                ? lifecycle.getAsJsonArray("midGameAssignmentRequests")
                : new JsonArray();

            JsonObject request = new JsonObject();
            request.addProperty("id", requestId);
            request.addProperty("playerUuid", playerId.toString());
            request.addProperty("teamLabel", teamLabel == null ? "" : teamLabel);
            request.addProperty("role", role == null ? "" : role);
            request.addProperty("requestedBy", requestedBy == null ? "" : requestedBy);
            request.addProperty("requestedAt", System.currentTimeMillis());
            requests.add(request);
            lifecycle.add("midGameAssignmentRequests", requests);
        });
    }

    public static synchronized List<MidGameAssignmentRequest> loadMidGameAssignmentRequests() {
        Path sessionsRoot = sessionsRoot();
        if (!Files.exists(sessionsRoot)) {
            return List.of();
        }

        List<MidGameAssignmentRequest> requests = new ArrayList<>();
        try (var stream = Files.list(sessionsRoot)) {
            stream.filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(path -> {
                    String sessionId = path.getFileName().toString();
                    Optional<JsonObject> json = readJson(sessionId);
                    if (json.isEmpty()) {
                        return;
                    }
                    requests.addAll(midGameAssignmentRequests(sessionId, lifecycleObject(json.get())));
                });
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to read mid-game assignment requests", e);
        }
        return requests;
    }

    public static synchronized void removeMidGameAssignmentRequest(String sessionId, String requestId) {
        if (sessionId == null || sessionId.isBlank() || requestId == null || requestId.isBlank()) {
            return;
        }
        updateLifecycleObject(sessionId, lifecycle -> {
            if (!lifecycle.has("midGameAssignmentRequests") || !lifecycle.get("midGameAssignmentRequests").isJsonArray()) {
                return;
            }
            JsonArray kept = new JsonArray();
            for (JsonElement element : lifecycle.getAsJsonArray("midGameAssignmentRequests")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject request = element.getAsJsonObject();
                if (!requestId.equals(SessionConfigJson.string(request, "id", ""))) {
                    kept.add(request);
                }
            }
            lifecycle.add("midGameAssignmentRequests", kept);
        });
    }

    public static synchronized void markRecoveryDetected(SessionRecoveryService.Candidate candidate) {
        if (candidate == null) {
            return;
        }
        updateRecoveryObject(candidate.sessionId(), recovery -> {
            recovery.addProperty("status", "DETECTED");
            recovery.addProperty("savePath", candidate.savePath().toAbsolutePath().normalize().toString());
            recovery.addProperty("gameType", candidate.gameType());
            recovery.addProperty("gameState", candidate.gameState());
            recovery.addProperty("saveReason", candidate.saveReason());
            recovery.addProperty("savedAt", candidate.savedAt());
            recovery.addProperty("detectedAt", Instant.now().toString());
        });
    }

    public static synchronized void markRecoveryComplete(String sessionId) {
        updateRecoveryObject(sessionId, recovery -> {
            recovery.addProperty("status", "RELAUNCHED");
            recovery.addProperty("completedAt", Instant.now().toString());
        });
    }

    public static synchronized void markRecoveryFailed(String sessionId, String message) {
        updateRecoveryObject(sessionId, recovery -> {
            recovery.addProperty("status", "FAILED");
            recovery.addProperty("failedAt", Instant.now().toString());
            recovery.addProperty("error", message == null ? "" : message);
        });
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
        cachedSnapshots = null;
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        deleteRecursively(sessionRoot);
    }

    public static synchronized void cleanupSessionsOnStartup() {
        cachedSnapshots = null;
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

        SessionRetentionConfig retention = SessionRetentionConfig.getInstance();
        if (retention.maxAgeDays() <= 0) {
            return;
        }
        long cutoffMillis = Instant.now().minus(retention.maxAgeDays(), ChronoUnit.DAYS).toEpochMilli();
        List<CleanupCandidate> sessionCandidates = entries.stream()
            .filter(Files::isDirectory)
            .map(path -> {
                long updatedAt = 0L;
                Optional<JsonObject> json = SessionConfigJson.read(path.resolve(SESSION_JSON_FILE_NAME));
                if (json.isPresent() && SessionConfigJson.longValue(json.get(), "cachedUpdatedAt", 0L) > 0L) {
                    updatedAt = SessionConfigJson.longValue(json.get(), "cachedUpdatedAt", 0L);
                } else {
                    updatedAt = MiniverseFileUtils.lastModifiedMillis(path);
                }
                return new CleanupCandidate(path, updatedAt);
            })
            .sorted(Comparator.comparingLong(CleanupCandidate::updatedAtMillis).reversed())
            .toList();
        Set<Path> retained = new HashSet<>();
        for (int index = 0; index < sessionCandidates.size(); index++) {
            CleanupCandidate candidate = sessionCandidates.get(index);
            if (candidate.updatedAtMillis() >= cutoffMillis) {
                retained.add(candidate.path());
            }
        }

        int removed = 0;
        for (Path entry : entries) {
            try {
                if (Files.isDirectory(entry)) {
                    if (retained.contains(entry)) {
                        Miniverse.LOGGER.debug("Session startup cleanup: retained {}", entry.getFileName());
                        continue;
                    }
                    deleteRecursively(entry);
                } else {
                    Files.deleteIfExists(entry);
                }
                removed++;
                Miniverse.LOGGER.debug("Session startup cleanup: removed {}", entry.getFileName());
            } catch (IOException e) {
                Miniverse.LOGGER.warn("Session startup cleanup: failed to remove {}", entry, e);
            }
        }

        Miniverse.LOGGER.info(
            "Session startup cleanup complete: retained {} session folder(s), removed {} stale item(s).",
            retained.size(),
            removed
        );
    }
    private static void writeSessionFile(Path sessionsRoot, GameSession session) throws IOException {
        cachedSnapshots = null;
        Path sessionRoot = sessionsRoot.resolve(session.getSessionId());
        Files.createDirectories(sessionRoot);

        boolean stopRequested = isStopRequested(session.getSessionId());
        boolean returnComplete = isReturnComplete(session.getSessionId());
        boolean seedChangeRequested = isSeedChangeRequested(session.getSessionId());
        boolean pauseRequested = isPauseRequested(session.getSessionId());
        JsonObject json = SessionConfigJson.baseSession(session);
        json.addProperty("playerCount", session.getGroups().stream().mapToInt(SessionGroup::getPlayerCount).sum());
        json.addProperty("groupCount", session.getGroups().size());
        json.addProperty("assignmentCount", session.getGroups().size());
        JsonObject lifecycle = SessionConfigJson.lifecycle(stopRequested, returnComplete, seedChangeRequested, pauseRequested);
        readJson(session.getSessionId())
            .map(SessionRegistry::lifecycleObject)
            .ifPresent(existing -> copyLifecycleHandoff(existing, lifecycle));
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

        long updatedAt = MiniverseFileUtils.lastModifiedMillis(sessionRoot);
        long createdAt = updatedAt;
        long launchedAt = updatedAt;
        long playedMillis = 0L;
        Optional<SaveMetadata> saveMetadata = latestMinigameSave(sessionRoot);
        if (saveMetadata.isPresent()) {
            SaveMetadata metadata = saveMetadata.get();
            long saveTimestamp = saveTimestamp(metadata);
            if (saveTimestamp > 0L) {
                updatedAt = saveTimestamp;
            }
            if (metadata.clockTicks() > 0L) {
                playedMillis = ticksToMillis(metadata.clockTicks());
            }
        }
        if (playedMillis <= 0L) {
            playedMillis = launchedAt > 0L ? Math.max(0L, updatedAt - launchedAt) : 0L;
        }
        return Optional.of(new Snapshot(sessionId, game, state, seed, playerCount, players, createdAt, launchedAt, updatedAt, playedMillis, isInspectable(sessionRoot)));
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
        if (SessionConfigJson.lifecycleFlag(object, "pauseRequested")) {
            state = "PAUSED";
        }
        long seed = SessionConfigJson.longValue(object, "seed", 0L);
        int playerCount = SessionConfigJson.integer(object, "playerCount", 0);
        long cachedUpdatedAt = SessionConfigJson.longValue(object, "cachedUpdatedAt", 0L);
        long cachedPlayedMillis = SessionConfigJson.longValue(object, "cachedPlayedMillis", -1L);

        long updatedAt;
        long playedMillis;
        long createdAt = SessionConfigJson.longValue(object, "createdAt", MiniverseFileUtils.lastModifiedMillis(sessionRoot));
        long launchedAt = SessionConfigJson.longValue(object, "launchedAt", 0L);

        if (cachedUpdatedAt > 0L && cachedPlayedMillis >= 0L) {
            updatedAt = cachedUpdatedAt;
            playedMillis = cachedPlayedMillis;
        } else {
            updatedAt = MiniverseFileUtils.lastModifiedMillis(sessionRoot);
            playedMillis = launchedAt > 0L ? Math.max(0L, updatedAt - launchedAt) : 0L;
            Optional<SaveMetadata> saveMetadata = latestMinigameSave(sessionRoot);
            if (saveMetadata.isPresent()) {
                SaveMetadata metadata = saveMetadata.get();
                long saveTimestamp = saveTimestamp(metadata);
                if (saveTimestamp > 0L) {
                    updatedAt = saveTimestamp;
                }
                if (metadata.clockTicks() > 0L) {
                    playedMillis = ticksToMillis(metadata.clockTicks());
                }
            }
        }

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

        return Optional.of(new Snapshot(sessionId, game, state, seed, playerCount, players, createdAt, launchedAt, updatedAt, playedMillis, isInspectable(sessionRoot)));
    }

    private static NbtCompound readSettingsNbt(JsonObject object, String sessionId) {
        String snbt = SessionConfigJson.string(object, "settingsNbt", "");
        if (snbt.isBlank()) {
            return new NbtCompound();
        }
        try {
            return StringNbtReader.parse(snbt);
        } catch (Exception e) {
            Miniverse.LOGGER.warn("Failed to parse retained session settings for {}", sessionId, e);
            return new NbtCompound();
        }
    }

    private static List<PlannedTeam> readTeams(JsonObject object, String sessionId) {
        JsonArray teamsJson = object.has("teams") && object.get("teams").isJsonArray()
            ? object.getAsJsonArray("teams")
            : new JsonArray();
        List<PlannedTeam> teams = new ArrayList<>();
        int index = 1;
        for (JsonElement teamElement : teamsJson) {
            if (!teamElement.isJsonObject()) {
                continue;
            }
            JsonObject teamObject = teamElement.getAsJsonObject();
            String label = SessionConfigJson.string(teamObject, "label", SessionConfigJson.string(teamObject, "displayName", "Team-" + index));
            JsonArray membersJson = teamObject.has("members") && teamObject.get("members").isJsonArray()
                ? teamObject.getAsJsonArray("members")
                : new JsonArray();
            List<SessionMembership> members = new ArrayList<>();
            for (JsonElement memberElement : membersJson) {
                if (!memberElement.isJsonObject()) {
                    continue;
                }
                JsonObject memberObject = memberElement.getAsJsonObject();
                String uuidText = SessionConfigJson.string(memberObject, "uuid", "");
                if (uuidText.isBlank()) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidText);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                String name = SessionConfigJson.string(memberObject, "name", "");
                String role = SessionConfigJson.string(memberObject, "role", "");
                members.add(new SessionMembership(uuid, name.isBlank() ? uuidText : name, role));
            }
            if (!members.isEmpty()) {
                teams.add(new PlannedTeam(label, members));
            }
            index++;
        }

        if (teams.isEmpty()) {
            Miniverse.LOGGER.warn("Retained session {} has no team roster in session metadata.", sessionId);
        }
        return teams;
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
            Boolean.parseBoolean(properties.getProperty("seedChangeRequested", "false")),
            Boolean.parseBoolean(properties.getProperty("pauseRequested", "false"))
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
        cachedSnapshots = null;
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
            boolean pauseRequested = SessionConfigJson.lifecycleFlag(json, "pauseRequested");
            JsonObject lifecycle = SessionConfigJson.lifecycle(requested, returnComplete, seedChangeRequested, pauseRequested);
            copyLifecycleHandoff(lifecycleObject(json), lifecycle);
            json.add("lifecycle", lifecycle);
            SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update session stop flag for {}", sessionId, e);
        }
    }

    private static void updateReturnComplete(String sessionId, boolean returnComplete) {
        cachedSnapshots = null;
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
            boolean pauseRequested = SessionConfigJson.lifecycleFlag(json, "pauseRequested");
            JsonObject lifecycle = SessionConfigJson.lifecycle(stopRequested, returnComplete, seedChangeRequested, pauseRequested);
            copyLifecycleHandoff(lifecycleObject(json), lifecycle);
            json.add("lifecycle", lifecycle);
            SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update session return flag for {}", sessionId, e);
        }
    }

    private static void updateSeedChangeRequested(String sessionId, boolean seedChangeRequested) {
        cachedSnapshots = null;
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
            boolean pauseRequested = SessionConfigJson.lifecycleFlag(json, "pauseRequested");
            JsonObject lifecycle = SessionConfigJson.lifecycle(stopRequested, returnComplete, seedChangeRequested, pauseRequested);
            copyLifecycleHandoff(lifecycleObject(json), lifecycle);
            json.add("lifecycle", lifecycle);
            SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update session seed change flag for {}", sessionId, e);
        }
    }

    private static void updatePauseRequested(String sessionId, boolean pauseRequested) {
        cachedSnapshots = null;
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
            boolean seedChangeRequested = SessionConfigJson.lifecycleFlag(json, "seedChangeRequested");
            JsonObject lifecycle = SessionConfigJson.lifecycle(stopRequested, returnComplete, seedChangeRequested, pauseRequested);
            copyLifecycleHandoff(lifecycleObject(json), lifecycle);
            json.add("lifecycle", lifecycle);
            SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update session pause flag for {}", sessionId, e);
        }
    }

    public static synchronized void computeAndWriteDisplayMetadata(String sessionId) {
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        if (!Files.isDirectory(sessionRoot)) {
            return;
        }
        Optional<SaveMetadata> saveMetadata = latestMinigameSave(sessionRoot);
        long updatedAt = MiniverseFileUtils.lastModifiedMillis(sessionRoot);
        long playedMillis = 0L;
        if (saveMetadata.isPresent()) {
            SaveMetadata metadata = saveMetadata.get();
            long saveTimestamp = saveTimestamp(metadata);
            if (saveTimestamp > 0L) {
                updatedAt = saveTimestamp;
            }
            if (metadata.clockTicks() > 0L) {
                playedMillis = ticksToMillis(metadata.clockTicks());
            }
        }
        writeDisplayMetadata(sessionId, updatedAt, playedMillis);
    }

    public static synchronized void writeDisplayMetadata(String sessionId, long updatedAtMillis, long playedMillis) {
        cachedSnapshots = null;
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        if (!Files.exists(sessionRoot)) {
            return;
        }
        try {
            Files.createDirectories(sessionRoot);
            JsonObject json = readJson(sessionId).orElseGet(JsonObject::new);
            json.addProperty("sessionId", sessionId);
            json.addProperty("cachedUpdatedAt", updatedAtMillis);
            json.addProperty("cachedPlayedMillis", playedMillis);
            SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to write display metadata for {}", sessionId, e);
        }
    }

    private static Path sessionsRoot() {
        if (SessionRuntimeConfig.isSessionServer()) {
            return SessionRuntimeConfig.getMainSessionsRoot().orElseGet(MiniversePaths::sessionsRoot);
        }
        return MiniversePaths.sessionsRoot();
    }

    private static void updateLifecycleObject(String sessionId, java.util.function.Consumer<JsonObject> updater) {
        cachedSnapshots = null;
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

    private static void updateRecoveryObject(String sessionId, java.util.function.Consumer<JsonObject> updater) {
        cachedSnapshots = null;
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        if (!Files.exists(sessionRoot)) {
            return;
        }

        try {
            Files.createDirectories(sessionRoot);
            JsonObject json = readJson(sessionId).orElseGet(JsonObject::new);
            json.addProperty("sessionId", sessionId);
            JsonObject recovery = json.has("recovery") && json.get("recovery").isJsonObject()
                ? json.getAsJsonObject("recovery")
                : new JsonObject();
            updater.accept(recovery);
            json.add("recovery", recovery);
            SessionConfigJson.write(sessionRoot.resolve(SESSION_JSON_FILE_NAME), json);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update recovery metadata for {}", sessionId, e);
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

    private static void recordPendingJoinNotice(String sessionId, UUID playerId, String playerName, long joinedAtMillis) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        updateLifecycleObject(sessionId, lifecycle -> {
            JsonArray notices = lifecycle.has("pendingJoiners") && lifecycle.get("pendingJoiners").isJsonArray()
                ? lifecycle.getAsJsonArray("pendingJoiners")
                : new JsonArray();
            removePendingJoinNotice(lifecycle, playerId);
            notices = lifecycle.has("pendingJoiners") && lifecycle.get("pendingJoiners").isJsonArray()
                ? lifecycle.getAsJsonArray("pendingJoiners")
                : notices;

            JsonObject notice = new JsonObject();
            notice.addProperty("playerUuid", playerId.toString());
            notice.addProperty("playerName", playerName == null || playerName.isBlank() ? playerId.toString() : playerName);
            notice.addProperty("joinedAt", joinedAtMillis);
            notices.add(notice);
            lifecycle.add("pendingJoiners", notices);
        });
    }

    private static void removePendingJoinNotice(JsonObject lifecycle, UUID playerId) {
        if (!lifecycle.has("pendingJoiners") || !lifecycle.get("pendingJoiners").isJsonArray()) {
            return;
        }
        JsonArray kept = new JsonArray();
        for (JsonElement element : lifecycle.getAsJsonArray("pendingJoiners")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject notice = element.getAsJsonObject();
            String uuidText = SessionConfigJson.string(notice, "playerUuid", SessionConfigJson.string(notice, "uuid", ""));
            if (!playerId.toString().equals(uuidText)) {
                kept.add(notice);
            }
        }
        lifecycle.add("pendingJoiners", kept);
    }

    private static List<PendingJoinNotice> pendingJoinNotices(JsonObject lifecycle) {
        if (!lifecycle.has("pendingJoiners") || !lifecycle.get("pendingJoiners").isJsonArray()) {
            return List.of();
        }
        List<PendingJoinNotice> notices = new ArrayList<>();
        for (JsonElement element : lifecycle.getAsJsonArray("pendingJoiners")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject notice = element.getAsJsonObject();
            String uuidText = SessionConfigJson.string(notice, "playerUuid", SessionConfigJson.string(notice, "uuid", ""));
            try {
                UUID playerId = UUID.fromString(uuidText);
                String playerName = SessionConfigJson.string(notice, "playerName", SessionConfigJson.string(notice, "name", uuidText));
                long joinedAt = SessionConfigJson.longValue(notice, "joinedAt", 0L);
                notices.add(new PendingJoinNotice(playerId, playerName, joinedAt));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return notices;
    }

    private static List<MidGameAssignmentRequest> midGameAssignmentRequests(String sessionId, JsonObject lifecycle) {
        if (!lifecycle.has("midGameAssignmentRequests") || !lifecycle.get("midGameAssignmentRequests").isJsonArray()) {
            return List.of();
        }
        List<MidGameAssignmentRequest> requests = new ArrayList<>();
        for (JsonElement element : lifecycle.getAsJsonArray("midGameAssignmentRequests")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject request = element.getAsJsonObject();
            String uuidText = SessionConfigJson.string(request, "playerUuid", "");
            try {
                requests.add(new MidGameAssignmentRequest(
                    SessionConfigJson.string(request, "id", ""),
                    sessionId,
                    UUID.fromString(uuidText),
                    SessionConfigJson.string(request, "teamLabel", ""),
                    SessionConfigJson.string(request, "role", ""),
                    SessionConfigJson.string(request, "requestedBy", ""),
                    SessionConfigJson.longValue(request, "requestedAt", 0L)
                ));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return requests;
    }

    private static void copySeedChangeHandoff(JsonObject sourceLifecycle, JsonObject targetLifecycle) {
        if (sourceLifecycle.has("seedChangeTargets")) {
            targetLifecycle.add("seedChangeTargets", sourceLifecycle.get("seedChangeTargets").deepCopy());
        }
        if (sourceLifecycle.has("seedChangeCompletedGroups")) {
            targetLifecycle.add("seedChangeCompletedGroups", sourceLifecycle.get("seedChangeCompletedGroups").deepCopy());
        }
    }

    private static void copyLifecycleHandoff(JsonObject sourceLifecycle, JsonObject targetLifecycle) {
        copySeedChangeHandoff(sourceLifecycle, targetLifecycle);
        if (sourceLifecycle.has("pendingJoiners")) {
            targetLifecycle.add("pendingJoiners", sourceLifecycle.get("pendingJoiners").deepCopy());
        }
        if (sourceLifecycle.has("midGameAssignmentRequests")) {
            targetLifecycle.add("midGameAssignmentRequests", sourceLifecycle.get("midGameAssignmentRequests").deepCopy());
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

    private static boolean isInspectable(Path sessionRoot) {
        try (var stream = Files.list(sessionRoot)) {
            return stream.anyMatch(path -> Files.isDirectory(path.resolve("world")));
        } catch (IOException ignored) {
            return false;
        }
    }


    private static void deleteRecursively(Path root) {
        try {
            MiniverseFileUtils.deleteRecursively(root);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to delete session registry root {}", root, e);
        }
    }

    private static Optional<SaveMetadata> latestMinigameSave(Path sessionRoot) {
        if (sessionRoot == null || !Files.isDirectory(sessionRoot)) {
            return Optional.empty();
        }
        try (var stream = Files.walk(sessionRoot, 4)) {
            return stream.filter(path -> path.getFileName().toString().equals(dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMinigameSessionStore().fileName()))
                .map(SessionRegistry::loadSaveMetadata)
                .flatMap(Optional::stream)
                .max(Comparator.comparingLong(SessionRegistry::saveTimestamp));
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to scan session {} for minigame saves", sessionRoot, e);
            return Optional.empty();
        }
    }

    private static Optional<SaveMetadata> loadSaveMetadata(Path savePath) {
        Optional<JsonObject> root = dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMinigameSessionStore().readFrom(savePath, false);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        long clockTicks = readClockTicks(root.get());
        long savedAtMillis = readSavedAtMillis(root.get());
        long lastModifiedMillis = MiniverseFileUtils.lastModifiedMillis(savePath);
        return Optional.of(new SaveMetadata(clockTicks, savedAtMillis, lastModifiedMillis));
    }

    private static long readClockTicks(JsonObject root) {
        if (root == null) {
            return 0L;
        }
        if (root.has("gameTicks") && root.get("gameTicks").isJsonPrimitive()) {
            return root.get("gameTicks").getAsLong();
        }
        if (root.has("clockTicks") && root.get("clockTicks").isJsonPrimitive()) {
            return root.get("clockTicks").getAsLong();
        }
        JsonObject metadata = root.has("metadata") && root.get("metadata").isJsonObject() ? root.getAsJsonObject("metadata") : null;
        if (metadata != null && metadata.has("clockTicks") && metadata.get("clockTicks").isJsonPrimitive()) {
            return metadata.get("clockTicks").getAsLong();
        }
        return 0L;
    }

    private static long readSavedAtMillis(JsonObject root) {
        if (root == null || !root.has("metadata") || !root.get("metadata").isJsonObject()) {
            return 0L;
        }
        JsonObject metadata = root.getAsJsonObject("metadata");
        String savedAt = SessionConfigJson.string(metadata, "savedAt", "");
        if (savedAt.isBlank()) {
            return 0L;
        }
        try {
            return Instant.parse(savedAt).toEpochMilli();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static long saveTimestamp(SaveMetadata metadata) {
        if (metadata == null) {
            return 0L;
        }
        return metadata.savedAtMillis() > 0L ? metadata.savedAtMillis() : metadata.lastModifiedMillis();
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(0L, ticks) * 50L;
    }
}
