package dev.frost.miniverse.session;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Runtime backend server state for one launched Minecraft server process.
 */
public final class BackendInstance {
    private volatile SessionState state;
    private volatile Integer port;
    private volatile Path workingDirectory;
    private volatile Process process;
    private volatile String connectionAddress;
    private volatile String lastError;
    private volatile Instant launchedAt;

    public BackendInstance() {
        this.state = SessionState.CREATED;
    }

    public SessionState getState() {
        return this.state;
    }

    public Integer getPort() {
        return this.port;
    }

    public Path getWorkingDirectory() {
        return this.workingDirectory;
    }

    public Process getProcess() {
        return this.process;
    }

    public String getConnectionAddress() {
        return this.connectionAddress;
    }

    public String getLastError() {
        return this.lastError;
    }

    public Instant getLaunchedAt() {
        return this.launchedAt;
    }

    public synchronized void markLaunching(Path workingDirectory, int port) {
        this.workingDirectory = workingDirectory;
        this.port = port;
        this.state = SessionState.LAUNCHING;
        this.lastError = null;
    }

    public synchronized void markRunning(Process process, String connectionAddress) {
        this.process = process;
        this.connectionAddress = connectionAddress;
        this.launchedAt = Instant.now();
        this.state = SessionState.RUNNING;
        this.lastError = null;
    }

    public synchronized void markStopped() {
        this.state = SessionState.STOPPED;
    }

    public synchronized void markFailed(String lastError) {
        this.state = SessionState.FAILED;
        this.lastError = lastError;
    }

    public boolean isAlive() {
        return this.process != null && this.process.isAlive();
    }
}
