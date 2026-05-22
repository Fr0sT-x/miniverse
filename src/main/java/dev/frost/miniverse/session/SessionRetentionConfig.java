package dev.frost.miniverse.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Controls how long completed or interrupted session folders remain available
 * for recovery, diagnostics, and inspection launches.
 */
public final class SessionRetentionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MiniversePaths.miniverseConfig("session-retention.json");

    private static SessionRetentionConfig instance;
    private Config config;

    private SessionRetentionConfig() {
        this.load();
    }

    public static synchronized SessionRetentionConfig getInstance() {
        if (instance == null) {
            instance = new SessionRetentionConfig();
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
            this.normalize();
            Miniverse.LOGGER.info("Loaded session retention configuration: {}", this.config);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to load session retention config, using defaults", e);
            this.config = Config.defaults();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this.config));
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to save session retention config", e);
        }
    }

    public synchronized void setKeepLatestSessions(int keepLatestSessions) {
        this.config.keepLatestSessions = Math.clamp(keepLatestSessions, 1, 50);
        this.save();
    }

    public synchronized void setMaxAgeDays(int maxAgeDays) {
        this.config.maxAgeDays = Math.clamp(maxAgeDays, 1, 365);
        this.save();
    }

    public int keepLatestSessions() {
        return this.config.keepLatestSessions;
    }

    public int maxAgeDays() {
        return this.config.maxAgeDays;
    }

    private void normalize() {
        this.config.keepLatestSessions = Math.clamp(this.config.keepLatestSessions, 1, 50);
        this.config.maxAgeDays = Math.clamp(this.config.maxAgeDays, 1, 365);
    }

    public static final class Config {
        public int keepLatestSessions;
        public int maxAgeDays;

        public Config(int keepLatestSessions, int maxAgeDays) {
            this.keepLatestSessions = keepLatestSessions;
            this.maxAgeDays = maxAgeDays;
        }

        public static Config defaults() {
            return new Config(3, 7);
        }

        @Override
        public String toString() {
            return "Config{" +
                "keepLatestSessions=" + this.keepLatestSessions +
                ", maxAgeDays=" + this.maxAgeDays +
                '}';
        }
    }
}
