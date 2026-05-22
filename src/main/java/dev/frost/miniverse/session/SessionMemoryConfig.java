package dev.frost.miniverse.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages memory allocation configuration for game sessions.
 * Allows configurable heap size per session type.
 */
public final class SessionMemoryConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MiniversePaths.miniverseConfig("session-memory.json");
    
    private static SessionMemoryConfig instance;
    private Config config;

    private SessionMemoryConfig() {
        this.load();
    }

    public static synchronized SessionMemoryConfig getInstance() {
        if (instance == null) {
            instance = new SessionMemoryConfig();
        }
        return instance;
    }

    private void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                this.config = GSON.fromJson(json, Config.class);
                if (this.config == null) {
                    this.config = Config.defaults();
                }
            } else {
                this.config = Config.defaults();
                this.save();
            }
            this.normalize();
            Miniverse.LOGGER.info("Loaded session memory configuration: {}", config);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to load session memory config, using defaults", e);
            this.config = Config.defaults();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(this.config);
            Files.writeString(CONFIG_PATH, json);
            Miniverse.LOGGER.info("Saved session memory configuration");
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to save session memory config", e);
        }
    }

    public synchronized void setMaxHeap(int gigabytes) {
        this.config.maxHeapGb = Math.max(1, Math.min(gigabytes, 128)); // Clamp between 1GB and 128GB
        this.save();
    }

    public synchronized void setInitialHeap(int gigabytes) {
        this.config.initialHeapGb = Math.max(1, Math.min(gigabytes, this.config.maxHeapGb));
        this.save();
    }

    public synchronized void setEnabled(boolean enabled) {
        this.config.enabled = enabled;
        this.save();
    }

    public int getMaxHeap() {
        return this.config.maxHeapGb;
    }

    public int getInitialHeap() {
        return this.config.initialHeapGb;
    }

    public boolean isEnabled() {
        return this.config.enabled;
    }

    public String getMaxHeapArg() {
        return "-Xmx" + this.config.maxHeapGb + "G";
    }

    public String getInitialHeapArg() {
        return "-Xms" + this.config.initialHeapGb + "G";
    }

    private void normalize() {
        this.config.maxHeapGb = Math.max(1, Math.min(this.config.maxHeapGb, 128));
        this.config.initialHeapGb = Math.max(1, Math.min(this.config.initialHeapGb, this.config.maxHeapGb));
    }

    public static final class Config {
        public int maxHeapGb;
        public int initialHeapGb;
        public boolean enabled;

        public Config(int maxHeapGb, int initialHeapGb, boolean enabled) {
            this.maxHeapGb = maxHeapGb;
            this.initialHeapGb = initialHeapGb;
            this.enabled = enabled;
        }

        public static Config defaults() {
            return new Config(2, 1, true); // Default: 2GB max, 1GB initial
        }

        @Override
        public String toString() {
            return "Config{" +
                    "maxHeapGb=" + maxHeapGb +
                    ", initialHeapGb=" + initialHeapGb +
                    ", enabled=" + enabled +
                    '}';
        }
    }
}

