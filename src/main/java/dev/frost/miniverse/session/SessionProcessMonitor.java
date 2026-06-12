package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniverseFileUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionProcessMonitor {
    private final SessionStore store;
    private final ServerLauncher launcher;
    private final Map<String, Process> inspectionProcesses = new ConcurrentHashMap<>();
    private final Map<String, ServerLauncher.MapEditorLaunchResult> mapEditorProcesses = new ConcurrentHashMap<>();
    private final Map<String, List<PendingBackendStop>> pendingSeedChangeStops = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor launcherExecutor;

    public record PendingBackendStop(String groupLabel, Process process) {
    }

    public SessionProcessMonitor(SessionStore store, ServerLauncher launcher) {
        this.store = store;
        this.launcher = launcher;
        this.launcherExecutor = this.createLauncherExecutor();
        this.registerShutdownHook();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Miniverse.LOGGER.info("Cleaning up backend session servers...");
            synchronized (this.store) {
                for (GameSession session : this.store.getSessions()) {
                    for (SessionGroup group : session.snapshotGroups()) {
                        if (group.isAlive()) {
                            Miniverse.LOGGER.info("Terminating backend process for {} in session {}", group.getDisplayName(), session.getSessionId());
                            this.launcher.stop(group);
                        }
                    }
                }
                for (Map.Entry<String, Process> entry : this.inspectionProcesses.entrySet()) {
                    Miniverse.LOGGER.info("Terminating inspection backend {}", entry.getKey());
                    this.stopProcess(entry.getValue());
                }
                this.inspectionProcesses.clear();
                for (Map.Entry<String, ServerLauncher.MapEditorLaunchResult> entry : this.mapEditorProcesses.entrySet()) {
                    Miniverse.LOGGER.info("Terminating map editor backend {}", entry.getKey());
                    this.stopProcess(entry.getValue().process());
                }
                this.mapEditorProcesses.clear();
            }
            this.launcherExecutor.shutdownNow();
            Miniverse.LOGGER.info("Backend session server cleanup complete.");
        }, "Miniverse-ShutdownHook"));
    }

    public ThreadPoolExecutor getLauncherExecutor() {
        return this.launcherExecutor;
    }

    public void addInspectionProcess(String key, Process process) {
        this.inspectionProcesses.put(key, process);
    }

    public void addMapEditorProcess(String key, ServerLauncher.MapEditorLaunchResult result) {
        this.mapEditorProcesses.put(key, result);
    }

    public void addPendingSeedChangeStops(String sessionId, List<PendingBackendStop> stops) {
        this.pendingSeedChangeStops.put(sessionId, stops);
    }

    public List<PendingBackendStop> removePendingSeedChangeStops(String sessionId) {
        return this.pendingSeedChangeStops.remove(sessionId);
    }

    private ThreadPoolExecutor createLauncherExecutor() {
        SessionLauncherConfig config = SessionLauncherConfig.getInstance();
        AtomicInteger threadCounter = new AtomicInteger(1);
        return new ThreadPoolExecutor(
            config.maxConcurrentLaunches(),
            config.maxConcurrentLaunches(),
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(config.queueCapacity()),
            runnable -> {
                Thread thread = new Thread(runnable, "Miniverse-SessionLauncher-" + threadCounter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public synchronized void setMaxConcurrentLaunches(int maxConcurrentLaunches) {
        int normalized = Math.clamp(maxConcurrentLaunches, 1, 64);
        SessionLauncherConfig.getInstance().setMaxConcurrentLaunches(normalized);

        int currentCore = this.launcherExecutor.getCorePoolSize();
        if (normalized > currentCore) {
            this.launcherExecutor.setMaximumPoolSize(normalized);
            this.launcherExecutor.setCorePoolSize(normalized);
        } else {
            this.launcherExecutor.setCorePoolSize(normalized);
            this.launcherExecutor.setMaximumPoolSize(normalized);
        }
        Miniverse.LOGGER.info("Updated max concurrent session launches to {}", normalized);
    }

    public synchronized void stopSession(String sessionId) {
        GameSession session = this.store.getSession(sessionId).orElse(null);
        if (session == null) {
            return;
        }

        session.setState(SessionState.STOPPING);
        this.stopBackendProcesses(session);
        session.setState(SessionState.STOPPED);
        this.store.persistRegistry();
    }

    public void stopBackendProcesses(GameSession session) {
        for (SessionGroup group : session.snapshotGroups()) {
            this.launcher.stop(group);
        }
    }

    public void stopProcess(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    public int exitCode(Process process) {
        if (process == null || process.isAlive()) {
            return Integer.MIN_VALUE;
        }
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    public synchronized void reapDeadBackends(MinecraftServer server) {
        boolean changed = false;
        for (GameSession session : this.store.getSessions()) {
            if (session.getState() != SessionState.RUNNING && session.getState() != SessionState.LAUNCHING) {
                continue;
            }

            List<SessionGroup> deadGroups = session.snapshotGroups().stream()
                .filter(group -> group.getState() == SessionState.RUNNING || group.getState() == SessionState.LAUNCHING)
                .filter(group -> group.getProcess() != null && !group.getProcess().isAlive())
                .toList();
            if (deadGroups.isEmpty()) {
                continue;
            }

            session.setState(SessionState.FAILED);
            for (SessionGroup group : deadGroups) {
                int exitCode = this.exitCode(group.getProcess());
                String error = "Backend process exited unexpectedly" + (exitCode == Integer.MIN_VALUE ? "" : " with exit code " + exitCode);
                group.markFailed(error);
                Miniverse.LOGGER.warn(
                    "{} session {} backend {} is no longer alive: {}.",
                    session.getGameType().getDisplayName(),
                    session.getSessionId(),
                    group.getDisplayName(),
                    error
                );
            }

            Text message = Text.literal("Session backend stopped unexpectedly. Please create or launch a new session.").formatted(Formatting.RED);
            for (SessionGroup group : session.snapshotGroups()) {
                for (UUID playerUuid : group.getPlayerUuids()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                    if (player != null) {
                        player.sendMessage(message, false);
                    }
                    this.store.removePlayerSession(playerUuid, session.getSessionId());
                }
            }
            changed = true;
        }

        if (changed) {
            this.store.persistRegistry();
        }

        var editorIter = this.mapEditorProcesses.entrySet().iterator();
        while (editorIter.hasNext()) {
            Map.Entry<String, ServerLauncher.MapEditorLaunchResult> entry = editorIter.next();
            if (!entry.getValue().process().isAlive()) {
                editorIter.remove();
                try {
                    MiniverseFileUtils.deleteRecursively(entry.getValue().workingDirectory());
                    Miniverse.LOGGER.info("Cleaned up dead map editor instance for {}", entry.getKey());
                } catch (IOException e) {
                    Miniverse.LOGGER.warn("Failed to clean up map editor directory for {}", entry.getKey(), e);
                }
            }
        }
    }
}
