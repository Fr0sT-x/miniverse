package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class SessionCrashTracker {
    public record CrashInfo(
        String sessionId,
        String gameDisplayName,
        String groupLabel,
        int exitCode,
        Path workingDirectory,
        Path crashReportPath,
        Path logFilePath,
        Path archivedDir,
        Instant timestamp
    ) {}

    private static final Map<String, CrashInfo> recentCrashes = new ConcurrentHashMap<>();
    private static volatile CrashInfo latestCrash = null;

    private SessionCrashTracker() {}

    public static List<String> getCrashedSessionIds() {
        return new ArrayList<>(recentCrashes.keySet());
    }

    public static Optional<CrashInfo> getCrash(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.ofNullable(latestCrash);
        }
        CrashInfo direct = recentCrashes.get(sessionId);
        if (direct != null) {
            return Optional.of(direct);
        }

        // Check archived crash folder on disk
        Path crashDir = MiniversePaths.crashesRoot().resolve(sessionId);
        if (Files.isDirectory(crashDir)) {
            Path report = findNewestCrashReport(crashDir);
            Path log = findLogFile(crashDir);
            return Optional.of(new CrashInfo(
                sessionId,
                "Unknown",
                "players",
                -1,
                crashDir,
                report,
                log,
                crashDir,
                Instant.now()
            ));
        }

        return Optional.empty();
    }

    public static void handleBackendCrash(MinecraftServer server, GameSession session, SessionGroup group, int exitCode) {
        Path workingDir = group.getWorkingDirectory();
        if (workingDir == null) {
            return;
        }

        Path crashReport = findNewestCrashReport(workingDir);
        Path logFile = findLogFile(workingDir);

        // 1. Copy to permanent archive
        Path archiveDir = MiniversePaths.crashesRoot().resolve(session.getSessionId()).resolve(group.getGroupLabel());
        try {
            Files.createDirectories(archiveDir);
            if (crashReport != null && Files.exists(crashReport)) {
                Files.copy(crashReport, archiveDir.resolve(crashReport.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            if (logFile != null && Files.exists(logFile)) {
                Files.copy(logFile, archiveDir.resolve("latest.log"), StandardCopyOption.REPLACE_EXISTING);
            }
            Path stdout = workingDir.resolve("stdout.log");
            if (Files.exists(stdout)) {
                Files.copy(stdout, archiveDir.resolve("stdout.log"), StandardCopyOption.REPLACE_EXISTING);
            }
            Path stderr = workingDir.resolve("stderr.log");
            if (Files.exists(stderr)) {
                Files.copy(stderr, archiveDir.resolve("stderr.log"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to archive crash artifacts for session {}", session.getSessionId(), e);
        }

        CrashInfo info = new CrashInfo(
            session.getSessionId(),
            session.getGameType().getDisplayName(),
            group.getDisplayName(),
            exitCode,
            workingDir,
            crashReport != null ? crashReport : archiveDir.resolve("crash-reports"),
            logFile != null ? logFile : archiveDir.resolve("latest.log"),
            archiveDir,
            Instant.now()
        );

        recentCrashes.put(session.getSessionId(), info);
        latestCrash = info;

        // 2. Alert all online admins
        notifyAdmins(server, info);
    }

    private static void notifyAdmins(MinecraftServer server, CrashInfo info) {
        String exitStr = info.exitCode() == Integer.MIN_VALUE ? "abruptly" : "exit code " + info.exitCode();

        MutableText header = Text.literal("⚠ [Miniverse Alert] ").formatted(Formatting.RED, Formatting.BOLD)
            .append(Text.literal("Backend session ").formatted(Formatting.YELLOW))
            .append(Text.literal(info.sessionId()).formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal(" (" + info.gameDisplayName() + ") crashed (" + exitStr + ")!").formatted(Formatting.RED));

        MutableText reportLine = null;
        if (info.crashReportPath() != null && Files.exists(info.crashReportPath())) {
            String fileName = info.crashReportPath().getFileName().toString();
            String absPath = info.crashReportPath().toAbsolutePath().toString();
            reportLine = Text.literal("  Crash Report: ").formatted(Formatting.GRAY)
                .append(Text.literal(fileName).formatted(Formatting.YELLOW))
                .append(Text.literal(" "))
                .append(Text.literal("[Copy Path]").formatted(Formatting.AQUA, Formatting.BOLD)
                    .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, absPath))
                                  .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy file path to clipboard:\n" + absPath)))))
                .append(Text.literal(" "))
                .append(Text.literal("[View in Chat]").formatted(Formatting.GREEN, Formatting.BOLD)
                    .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/session crash " + info.sessionId()))
                                  .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to view crash summary in chat")))));
        }

        MutableText logLine = null;
        Path displayLog = (info.logFilePath() != null && Files.exists(info.logFilePath())) ? info.logFilePath() : info.archivedDir().resolve("latest.log");
        if (displayLog != null && Files.exists(displayLog)) {
            String absLogPath = displayLog.toAbsolutePath().toString();
            logLine = Text.literal("  Session Log: ").formatted(Formatting.GRAY)
                .append(Text.literal("latest.log").formatted(Formatting.YELLOW))
                .append(Text.literal(" "))
                .append(Text.literal("[Copy Path]").formatted(Formatting.AQUA, Formatting.BOLD)
                    .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, absLogPath))
                                  .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy log path to clipboard:\n" + absLogPath)))))
                .append(Text.literal(" "))
                .append(Text.literal("[Tail Log]").formatted(Formatting.GREEN, Formatting.BOLD)
                    .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/session logs " + info.sessionId() + " 25"))
                                  .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to view recent logs in chat")))));
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (SessionPermissions.canManageSessions(player)) {
                player.sendMessage(header, false);
                if (reportLine != null) {
                    player.sendMessage(reportLine, false);
                }
                if (logLine != null) {
                    player.sendMessage(logLine, false);
                }
            }
        }
    }

    public static int showCrashSummary(ServerCommandSource source, String sessionId) {
        CrashInfo info = getCrash(sessionId).orElse(null);
        if (info == null) {
            source.sendError(Text.literal("No recorded crash found for session '" + (sessionId != null ? sessionId : "latest") + "'."));
            return 0;
        }

        Path reportFile = info.crashReportPath();
        if (reportFile == null || !Files.exists(reportFile)) {
            reportFile = findNewestCrashReport(info.archivedDir());
        }
        if (reportFile == null || !Files.exists(reportFile)) {
            reportFile = findNewestCrashReport(info.workingDirectory());
        }

        source.sendMessage(Text.literal("══════════ [Crash Report: " + info.sessionId() + "] ══════════").formatted(Formatting.RED, Formatting.BOLD));

        if (reportFile != null && Files.exists(reportFile)) {
            try {
                List<String> lines = Files.readAllLines(reportFile);
                String description = "";
                List<String> stackTrace = new ArrayList<>();
                boolean capturing = false;

                for (String line : lines) {
                    if (line.startsWith("Description: ")) {
                        description = line;
                    }
                    if (line.startsWith("java.") || line.startsWith("net.") || line.contains("Exception") || line.contains("Error")) {
                        capturing = true;
                    }
                    if (capturing) {
                        stackTrace.add(line);
                        if (stackTrace.size() >= 14 || line.startsWith("A detailed walkthrough")) {
                            break;
                        }
                    }
                }

                if (!description.isEmpty()) {
                    source.sendMessage(Text.literal(description).formatted(Formatting.GOLD));
                }
                for (String trace : stackTrace) {
                    if (trace.startsWith("A detailed walkthrough")) break;
                    if (trace.startsWith("Caused by:")) {
                        source.sendMessage(Text.literal(trace).formatted(Formatting.RED, Formatting.BOLD));
                    } else if (trace.startsWith("\tat ")) {
                        source.sendMessage(Text.literal("  " + trace.trim()).formatted(Formatting.GRAY));
                    } else {
                        source.sendMessage(Text.literal(trace).formatted(Formatting.RED));
                    }
                }

                String abs = reportFile.toAbsolutePath().toString();
                MutableText copyText = Text.literal("[Copy Full Crash File Path]").formatted(Formatting.AQUA, Formatting.UNDERLINE)
                    .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, abs))
                                  .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Copy path to clipboard:\n" + abs))));
                source.sendMessage(copyText);
                return 1;
            } catch (IOException e) {
                source.sendError(Text.literal("Failed to read crash report file: " + e.getMessage()));
                return 0;
            }
        }

        // Fallback: read stderr.log or latest.log
        Path stderr = info.workingDirectory() != null ? info.workingDirectory().resolve("stderr.log") : null;
        if (stderr == null || !Files.exists(stderr)) {
            stderr = info.archivedDir().resolve("stderr.log");
        }

        if (stderr != null && Files.exists(stderr)) {
            try {
                List<String> errLines = Files.readAllLines(stderr);
                if (!errLines.isEmpty()) {
                    source.sendMessage(Text.literal("Last error output (stderr.log):").formatted(Formatting.YELLOW));
                    int start = Math.max(0, errLines.size() - 12);
                    for (int i = start; i < errLines.size(); i++) {
                        source.sendMessage(Text.literal(errLines.get(i)).formatted(Formatting.RED));
                    }
                    return 1;
                }
            } catch (IOException ignored) {}
        }

        source.sendFeedback(() -> Text.literal("No JVM crash report file was generated. Exit code: " + info.exitCode()).formatted(Formatting.YELLOW), false);
        return 1;
    }

    public static int showSessionLogs(ServerCommandSource source, String sessionId, int linesCount) {
        int tail = Math.clamp(linesCount, 5, 50);

        Path logFile = null;
        CrashInfo crash = recentCrashes.get(sessionId);
        if (crash != null && crash.logFilePath() != null && Files.exists(crash.logFilePath())) {
            logFile = crash.logFilePath();
        }

        if (logFile == null) {
            Path archived = MiniversePaths.crashesRoot().resolve(sessionId);
            logFile = findLogFile(archived);
        }

        if (logFile == null) {
            Path sessionDir = MiniversePaths.sessionsRoot().resolve(sessionId);
            logFile = findLogFile(sessionDir);
        }

        if (logFile == null || !Files.exists(logFile)) {
            source.sendError(Text.literal("Could not locate log file for session '" + sessionId + "'."));
            return 0;
        }

        try {
            List<String> allLines = Files.readAllLines(logFile);
            source.sendMessage(Text.literal("══════════ [Log Tail: " + sessionId + " (last " + tail + " lines)] ══════════").formatted(Formatting.AQUA, Formatting.BOLD));
            int start = Math.max(0, allLines.size() - tail);
            for (int i = start; i < allLines.size(); i++) {
                String l = allLines.get(i);
                Formatting color = l.contains("/ERROR]") || l.contains("Exception") ? Formatting.RED
                    : l.contains("/WARN]") ? Formatting.YELLOW : Formatting.WHITE;
                source.sendMessage(Text.literal(l).formatted(color));
            }
            String abs = logFile.toAbsolutePath().toString();
            MutableText copyText = Text.literal("[Copy Log Path]").formatted(Formatting.AQUA, Formatting.UNDERLINE)
                .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, abs))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Copy path to clipboard:\n" + abs))));
            source.sendMessage(copyText);
            return 1;
        } catch (IOException e) {
            source.sendError(Text.literal("Failed to read log file: " + e.getMessage()));
            return 0;
        }
    }

    private static Path findNewestCrashReport(Path root) {
        if (root == null || !Files.exists(root)) return null;
        Path reportsDir = Files.isDirectory(root.resolve("crash-reports")) ? root.resolve("crash-reports") : root;
        try (Stream<Path> stream = Files.walk(reportsDir, 2)) {
            return stream
                .filter(p -> p.getFileName().toString().startsWith("crash-") && p.getFileName().toString().endsWith(".txt"))
                .max(Comparator.comparingLong(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis();
                    } catch (IOException e) {
                        return 0L;
                    }
                }))
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static Path findLogFile(Path root) {
        if (root == null || !Files.exists(root)) return null;
        Path logsDir = root.resolve("logs").resolve("latest.log");
        if (Files.exists(logsDir)) return logsDir;
        Path stdout = root.resolve("stdout.log");
        if (Files.exists(stdout)) return stdout;

        try (Stream<Path> stream = Files.walk(root, 3)) {
            return stream
                .filter(p -> p.getFileName().toString().equals("latest.log") || p.getFileName().toString().equals("stdout.log"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
