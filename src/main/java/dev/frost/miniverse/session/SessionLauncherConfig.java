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

    public static final class Config {
        public int maxConcurrentLaunches;
        public int queueCapacity;

        public Config(int maxConcurrentLaunches, int queueCapacity) {
            this.maxConcurrentLaunches = maxConcurrentLaunches;
            this.queueCapacity = queueCapacity;
        }

        public static Config defaults() {
            return new Config(2, 64);
        }

        private void normalize() {
            this.maxConcurrentLaunches = Math.clamp(this.maxConcurrentLaunches, 1, 64);
            this.queueCapacity = Math.clamp(this.queueCapacity, 1, 1024);
        }

        @Override
        public String toString() {
            return "Config{" +
                "maxConcurrentLaunches=" + this.maxConcurrentLaunches +
                ", queueCapacity=" + this.queueCapacity +
                '}';
        }
    }
}
