package dev.frost.miniverse.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VelocityProxyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MiniversePaths.miniverseConfig("proxy.json");

    private static VelocityProxyConfig instance;
    private Config config;

    private VelocityProxyConfig() {
        this.load();
    }

    public static synchronized VelocityProxyConfig getInstance() {
        if (instance == null) {
            instance = new VelocityProxyConfig();
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
            Miniverse.LOGGER.info("Loaded proxy configuration: {}", this.config);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to load proxy config, using defaults", e);
            this.config = Config.defaults();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this.config));
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to save proxy config", e);
        }
    }

    public boolean velocityEnabled() {
        return SessionRuntimeConfig.getProxyVelocityEnabled().orElse(this.config.velocityEnabled);
    }

    public String lobbyServerName() {
        return SessionRuntimeConfig.getProxyLobbyServerName().orElse(this.config.lobbyServerName);
    }

    public String backendHost() {
        return SessionRuntimeConfig.getProxyBackendHost().orElse(this.config.backendHost);
    }

    public String serverNamePrefix() {
        return SessionRuntimeConfig.getProxyServerNamePrefix().orElse(this.config.serverNamePrefix);
    }

    public String serverName(String sessionId, String groupLabel) {
        return sanitize(this.serverNamePrefix()) + "-" + sanitize(sessionId) + "-" + sanitize(groupLabel);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "server";
        }
        String sanitized = value.toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        return sanitized.replaceAll("-+", "-").replaceAll("(^-|-$)", "");
    }

    public static final class Config {
        public boolean velocityEnabled;
        public String lobbyServerName;
        public String backendHost;
        public String serverNamePrefix;

        public Config(boolean velocityEnabled, String lobbyServerName, String backendHost, String serverNamePrefix) {
            this.velocityEnabled = velocityEnabled;
            this.lobbyServerName = lobbyServerName;
            this.backendHost = backendHost;
            this.serverNamePrefix = serverNamePrefix;
        }

        public static Config defaults() {
            return new Config(false, "lobby", "127.0.0.1", "miniverse");
        }

        private void normalize() {
            this.lobbyServerName = normalize(this.lobbyServerName, "lobby");
            this.backendHost = normalize(this.backendHost, "127.0.0.1");
            this.serverNamePrefix = normalize(this.serverNamePrefix, "miniverse");
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.trim().isBlank() ? fallback : value.trim();
        }

        @Override
        public String toString() {
            return "Config{" +
                "velocityEnabled=" + this.velocityEnabled +
                ", lobbyServerName='" + this.lobbyServerName + '\'' +
                ", backendHost='" + this.backendHost + '\'' +
                ", serverNamePrefix='" + this.serverNamePrefix + '\'' +
                '}';
        }
    }
}
