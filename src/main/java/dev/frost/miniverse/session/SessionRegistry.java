package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public final class SessionRegistry {
    private static final String SESSION_FILE_NAME = "session.properties";

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
                    Optional<Properties> properties = readProperties(sessionId);
                    if (properties.isEmpty()) {
                        return;
                    }
                    Properties props = properties.get();
                    boolean stopRequested = Boolean.parseBoolean(props.getProperty("stopRequested", "false"));
                    boolean returnComplete = Boolean.parseBoolean(props.getProperty("returnComplete", "false"));
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
        return readProperties(sessionId).map(properties -> Boolean.parseBoolean(properties.getProperty("stopRequested", "false"))).orElse(false);
    }

    public static synchronized void markReturnComplete(String sessionId) {
        updateReturnComplete(sessionId, true);
    }

    public static synchronized void clearReturnComplete(String sessionId) {
        updateReturnComplete(sessionId, false);
    }

    public static synchronized boolean isReturnComplete(String sessionId) {
        return readProperties(sessionId).map(properties -> Boolean.parseBoolean(properties.getProperty("returnComplete", "false"))).orElse(false);
    }

    public static synchronized void removeSession(String sessionId) {
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        deleteRecursively(sessionRoot);
    }

    private static void writeSessionFile(Path sessionsRoot, GameSession session) throws IOException {
        Path sessionRoot = sessionsRoot.resolve(session.getSessionId());
        Files.createDirectories(sessionRoot);

        Properties properties = readProperties(session.getSessionId()).orElseGet(Properties::new);
        properties.setProperty("sessionId", session.getSessionId());
        properties.setProperty("game", session.getGameType().getCommandName());
        properties.setProperty("state", session.getState().name());
        properties.setProperty("seed", Long.toString(session.getSeedPlan().sharedSeed()));
        properties.setProperty("playerCount", Integer.toString(session.getAssignments().stream().mapToInt(PlayerAssignment::getPlayerCount).sum()));
        properties.setProperty("assignmentCount", Integer.toString(session.getAssignments().size()));
        properties.setProperty("stopRequested", properties.getProperty("stopRequested", "false"));
        properties.setProperty("returnComplete", properties.getProperty("returnComplete", "false"));

        int index = 0;
        for (PlayerAssignment assignment : session.getAssignments()) {
            properties.setProperty("assignment." + index + ".label", assignment.getAssignmentLabel());
            properties.setProperty("assignment." + index + ".displayName", assignment.getDisplayName());
            properties.setProperty("assignment." + index + ".state", assignment.getState().name());
            properties.setProperty("assignment." + index + ".playerCount", Integer.toString(assignment.getPlayerCount()));
            index++;
        }

        try (Writer writer = Files.newBufferedWriter(sessionRoot.resolve(SESSION_FILE_NAME))) {
            properties.store(writer, "Miniverse session snapshot");
        }
    }

    private static Optional<Snapshot> loadSnapshot(Path sessionRoot) {
        Path file = sessionRoot.resolve(SESSION_FILE_NAME);
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

        int assignmentCount = parseInt(properties.getProperty("assignmentCount", "0"), 0);
        List<String> players = new ArrayList<>();
        for (int i = 0; i < assignmentCount; i++) {
            String displayName = properties.getProperty("assignment." + i + ".displayName", "");
            if (!displayName.isBlank()) {
                players.add(displayName);
            }
        }

        return Optional.of(new Snapshot(sessionId, game, state, seed, playerCount, players));
    }

    private static Optional<Properties> readProperties(String sessionId) {
        Path file = sessionsRoot().resolve(sessionId).resolve(SESSION_FILE_NAME);
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

    private static void updateStopRequested(String sessionId, boolean requested) {
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        if (!Files.exists(sessionRoot)) {
            return;
        }

        Properties properties = readProperties(sessionId).orElseGet(Properties::new);
        properties.setProperty("stopRequested", Boolean.toString(requested));

        try {
            Files.createDirectories(sessionRoot);
            try (Writer writer = Files.newBufferedWriter(sessionRoot.resolve(SESSION_FILE_NAME))) {
                properties.store(writer, "Miniverse session snapshot");
            }
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update session stop flag for {}", sessionId, e);
        }
    }

    private static void updateReturnComplete(String sessionId, boolean returnComplete) {
        Path sessionRoot = sessionsRoot().resolve(sessionId);
        if (!Files.exists(sessionRoot)) {
            return;
        }

        Properties properties = readProperties(sessionId).orElseGet(Properties::new);
        properties.setProperty("returnComplete", Boolean.toString(returnComplete));

        try {
            Files.createDirectories(sessionRoot);
            try (Writer writer = Files.newBufferedWriter(sessionRoot.resolve(SESSION_FILE_NAME))) {
                properties.store(writer, "Miniverse session snapshot");
            }
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to update session return flag for {}", sessionId, e);
        }
    }

    private static Path sessionsRoot() {
        return locateProjectRoot().resolve("run").resolve("sessions");
    }

    private static Path locateProjectRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("gradle.properties"))) {
                return current;
            }
            current = current.getParent();
        }
        return Paths.get("").toAbsolutePath();
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
