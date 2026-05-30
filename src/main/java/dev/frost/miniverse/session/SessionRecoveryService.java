package dev.frost.miniverse.session;

import com.google.gson.JsonObject;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;
import dev.frost.miniverse.minigame.core.MinigameSessionStore;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SessionRecoveryService {
    private static final boolean AUTO_RELAUNCH = Boolean.parseBoolean(System.getProperty("miniverse.recovery.autoRelaunch", "false"));

    private SessionRecoveryService() {
    }

    public record Candidate(String sessionId, Path savePath, String gameType, String gameState, String saveReason, String savedAt) {
    }

    public static List<Candidate> scanRecoverableSaves() {
        Path sessionsRoot = MiniversePaths.sessionsRoot();
        if (!Files.exists(sessionsRoot)) {
            return List.of();
        }

        Map<String, Candidate> newestBySession = new LinkedHashMap<>();
        try (var stream = Files.walk(sessionsRoot, 4)) {
            stream.filter(path -> path.getFileName().toString().equals(MinigameSessionStore.fileName()))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> candidateFrom(sessionsRoot, path).ifPresent(candidate -> {
                    Candidate previous = newestBySession.get(candidate.sessionId());
                    if (previous == null || compareSavedAt(candidate, previous) >= 0) {
                        newestBySession.put(candidate.sessionId(), candidate);
                    }
                }));
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to scan sessions root {} for recovery saves.", sessionsRoot, e);
        }
        return List.copyOf(newestBySession.values());
    }

    public static void recoverUnfinishedSessions(MinecraftServer server) {
        if (SessionRuntimeConfig.isSessionServer()) {
            return;
        }

        List<Candidate> candidates = scanRecoverableSaves();
        if (candidates.isEmpty()) {
            Miniverse.LOGGER.info("Session recovery: no recoverable minigame saves found.");
            return;
        }

        Miniverse.LOGGER.info("Session recovery: found {} recoverable save candidate(s).", candidates.size());
        for (Candidate candidate : candidates) {
            SessionRegistry.markRecoveryDetected(candidate);

            Optional<SessionRegistry.Snapshot> snapshot = SessionRegistry.loadSnapshot(candidate.sessionId());
            if (snapshot.isEmpty()) {
                Miniverse.LOGGER.warn("Session recovery: save {} has no registry metadata for session {}.", candidate.savePath(), candidate.sessionId());
                continue;
            }
            if (!isUnfinished(snapshot.get())) {
                Miniverse.LOGGER.info("Session recovery: session {} is not unfinished (state {}).", candidate.sessionId(), snapshot.get().state());
                continue;
            }
            if (SessionRegistry.isStopRequested(candidate.sessionId()) || SessionRegistry.isReturnComplete(candidate.sessionId())) {
                Miniverse.LOGGER.info("Session recovery: session {} is stopping or already returned; skipping auto relaunch.", candidate.sessionId());
                continue;
            }
            if (!AUTO_RELAUNCH) {
                Miniverse.LOGGER.info("Session recovery: session {} is recoverable. Auto relaunch is disabled; use the retained session history to relaunch it manually.", candidate.sessionId());
                continue;
            }

            Miniverse.LOGGER.info("Session recovery: relaunching unfinished session {} from save {}.", candidate.sessionId(), candidate.savePath());
            SessionManager.getInstance().relaunchRetainedSession(candidate.sessionId(), server)
                .whenComplete((session, error) -> server.execute(() -> {
                    if (error != null) {
                        SessionRegistry.markRecoveryFailed(candidate.sessionId(), error.getMessage());
                        Miniverse.LOGGER.warn("Session recovery: failed to relaunch session {}.", candidate.sessionId(), error);
                        return;
                    }
                    SessionManager.getInstance().transferAssignedPlayers(server, session);
                    SessionRegistry.markRecoveryComplete(candidate.sessionId());
                    Miniverse.LOGGER.info("Session recovery: relaunched session {}.", candidate.sessionId());
                }));
        }
    }

    private static Optional<Candidate> candidateFrom(Path sessionsRoot, Path savePath) {
        Optional<JsonObject> root = MinigameSessionStore.readFrom(savePath, true);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        String sessionId = stringValue(root.get(), "sessionId", "");
        if (sessionId.isBlank()) {
            Path relative = sessionsRoot.relativize(savePath);
            if (relative.getNameCount() > 0) {
                sessionId = relative.getName(0).toString();
            }
        }
        if (sessionId.isBlank()) {
            Miniverse.LOGGER.warn("Session recovery: save {} has no session id.", savePath);
            return Optional.empty();
        }

        JsonObject metadata = root.get().has("metadata") && root.get().get("metadata").isJsonObject()
            ? root.get().getAsJsonObject("metadata")
            : new JsonObject();
        return Optional.of(new Candidate(
            sessionId,
            savePath,
            stringValue(metadata, "gameType", stringValue(root.get(), "gameType", stringValue(root.get(), "game", ""))),
            stringValue(metadata, "gameState", stringValue(root.get(), "gameState", stringValue(root.get(), "state", ""))),
            stringValue(metadata, "saveReason", ""),
            stringValue(metadata, "savedAt", stringValue(root.get(), "savedAt", ""))
        ));
    }

    private static boolean isUnfinished(SessionRegistry.Snapshot snapshot) {
        String state = snapshot.state() == null ? "" : snapshot.state().toUpperCase();
        return state.equals("RUNNING") || state.equals("LAUNCHING") || state.equals("PAUSED") || state.equals("FAILED");
    }

    private static int compareSavedAt(Candidate left, Candidate right) {
        return parseInstant(left.savedAt()).compareTo(parseInstant(right.savedAt()));
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
}
