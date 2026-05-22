package dev.frost.miniverse.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SessionLauncherConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MiniversePaths.miniverseConfig("session-launcher.json");

    private static SessionLauncherConfig instance;
    private Config config;

    private SessionLauncherConfig() {
        this.load();
    }

    public static synchronized SessionLauncherConfig getInstance() {
        if (instance == null) {
            instance = new SessionLauncherConfig();
        }
        return instance;
    }

    private void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                this.config = GSON.fromJson(Files.readString(CONFIG_PATH), Config.class);
                if (this.config == null) {
                    this.config = Config.defaults();
                }
            } else {
                this.config = Config.defaults();
                this.save();
            }
            this.config.normalize();
            Miniverse.LOGGER.info("Loaded session launcher configuration: {}", this.config);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to load session launcher config, using defaults", e);
            this.config = Config.defaults();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this.config));
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to save session launcher config", e);
        }
    }

    public int maxConcurrentLaunches() {
        return this.config.maxConcurrentLaunches;
    }

    public synchronized void setMaxConcurrentLaunches(int maxConcurrentLaunches) {
        this.config.maxConcurrentLaunches = maxConcurrentLaunches;
        this.config.normalize();
        this.save();
    }

    public int queueCapacity() {
        return this.config.queueCapacity;
    }

    public int sessionPortStart() {
        return this.config.sessionPortStart;
    }

    public int sessionPortEnd() {
        return this.config.sessionPortEnd;
    }

    public int publicSessionPortStart() {
        return this.config.publicSessionPortStart;
    }

    public int publicSessionPortEnd() {
        return this.config.publicSessionPortEnd;
    }

    public boolean hasSessionPortRange() {
        return this.config.sessionPortStart > 0 && this.config.sessionPortEnd >= this.config.sessionPortStart;
    }

    public boolean hasPublicSessionPortRange() {
        return this.config.publicSessionPortStart > 0 && this.config.publicSessionPortEnd >= this.config.publicSessionPortStart;
    }

    public int publicPortForLocalPort(int localPort) {
        if (!this.hasSessionPortRange() || !this.hasPublicSessionPortRange()) {
            return localPort;
        }

        if (localPort < this.config.sessionPortStart || localPort > this.config.sessionPortEnd) {
            return localPort;
        }

        return this.config.publicSessionPortStart + (localPort - this.config.sessionPortStart);
    }

    public static final class Config {
        public int maxConcurrentLaunches;
        public int queueCapacity;
        public int sessionPortStart;
        public int sessionPortEnd;
        public int publicSessionPortStart;
        public int publicSessionPortEnd;

        public Config(int maxConcurrentLaunches, int queueCapacity) {
            this(maxConcurrentLaunches, queueCapacity, 0, 0, 0, 0);
        }

        public Config(int maxConcurrentLaunches, int queueCapacity, int sessionPortStart, int sessionPortEnd) {
            this(maxConcurrentLaunches, queueCapacity, sessionPortStart, sessionPortEnd, 0, 0);
        }

        public Config(int maxConcurrentLaunches, int queueCapacity, int sessionPortStart, int sessionPortEnd, int publicSessionPortStart, int publicSessionPortEnd) {
            this.maxConcurrentLaunches = maxConcurrentLaunches;
            this.queueCapacity = queueCapacity;
            this.sessionPortStart = sessionPortStart;
            this.sessionPortEnd = sessionPortEnd;
            this.publicSessionPortStart = publicSessionPortStart;
            this.publicSessionPortEnd = publicSessionPortEnd;
        }

        public static Config defaults() {
            return new Config(2, 64);
        }

        private void normalize() {
            this.maxConcurrentLaunches = Math.clamp(this.maxConcurrentLaunches, 1, 64);
            this.queueCapacity = Math.clamp(this.queueCapacity, 1, 1024);
            if (this.sessionPortStart <= 0 || this.sessionPortEnd <= 0) {
                this.sessionPortStart = 0;
                this.sessionPortEnd = 0;
                return;
            }
            this.sessionPortStart = Math.clamp(this.sessionPortStart, 1024, 65535);
            this.sessionPortEnd = Math.clamp(this.sessionPortEnd, this.sessionPortStart, 65535);
            if (this.publicSessionPortStart <= 0 || this.publicSessionPortEnd <= 0) {
                this.publicSessionPortStart = 0;
                this.publicSessionPortEnd = 0;
                return;
            }
            int localRangeSize = this.sessionPortEnd - this.sessionPortStart;
            int highestPublicStart = Math.max(1024, 65535 - localRangeSize);
            this.publicSessionPortStart = Math.clamp(this.publicSessionPortStart, 1024, highestPublicStart);
            this.publicSessionPortEnd = Math.clamp(this.publicSessionPortEnd, this.publicSessionPortStart, 65535);
            if (this.publicSessionPortEnd - this.publicSessionPortStart != localRangeSize) {
                this.publicSessionPortEnd = this.publicSessionPortStart + localRangeSize;
            }
        }

        @Override
        public String toString() {
            return "Config{" +
                "maxConcurrentLaunches=" + this.maxConcurrentLaunches +
                ", queueCapacity=" + this.queueCapacity +
                ", sessionPortStart=" + this.sessionPortStart +
                ", sessionPortEnd=" + this.sessionPortEnd +
                ", publicSessionPortStart=" + this.publicSessionPortStart +
                ", publicSessionPortEnd=" + this.publicSessionPortEnd +
                '}';
        }
    }
}
