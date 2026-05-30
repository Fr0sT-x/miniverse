package dev.frost.miniverse.session;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

import com.google.gson.JsonObject;

public final class SessionRuntimeConfig {
    private static Properties config;

    private SessionRuntimeConfig() {
    }

    public static synchronized Optional<String> getSessionId() {
        String sessionId = getConfig().getProperty("sessionId", "");
        return sessionId.isBlank() ? Optional.empty() : Optional.of(sessionId);
    }

    public static synchronized BackendLaunchMode getLaunchMode() {
        if (Boolean.getBoolean("miniverse.inspection")) {
            return BackendLaunchMode.INSPECTION_SESSION;
        }
        return BackendLaunchMode.fromString(getConfig().getProperty("launchMode", ""));
    }

    public static synchronized String getReturnHost() {
        return getConfig().getProperty("return.host", "127.0.0.1");
    }

    public static synchronized String getGroupLabel() {
        return getConfig().getProperty("groupLabel", "");
    }

    public static synchronized int getReturnPort() {
        String value = getConfig().getProperty("return.port", "25565");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 25565;
        }
    }

    public static synchronized Optional<Boolean> getProxyVelocityEnabled() {
        String value = getConfig().getProperty("proxy.velocityEnabled", "");
        if (value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Boolean.parseBoolean(value));
    }

    public static synchronized Optional<String> getProxyLobbyServerName() {
        return optionalString("proxy.lobbyServerName");
    }

    public static synchronized Optional<String> getProxyBackendHost() {
        return optionalString("proxy.backendHost");
    }

    public static synchronized Optional<String> getProxyServerNamePrefix() {
        return optionalString("proxy.serverNamePrefix");
    }

    public static synchronized boolean isSessionServer() {
        return getSessionId().isPresent();
    }

    public static synchronized Optional<Path> getMainSessionsRoot() {
        String value = getConfig().getProperty("registry.sessionsRoot", "");
        if (value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(value).toAbsolutePath().normalize());
    }

    public static synchronized Optional<JsonObject> getSessionJson() {
        String configPath = System.getProperty("miniverse.session.config", "");
        if (configPath.isBlank() || !configPath.endsWith(".json")) {
            return Optional.empty();
        }

        return SessionConfigJson.read(Paths.get(configPath));
    }

    private static synchronized Properties getConfig() {
        if (config != null) {
            return config;
        }

        config = new Properties();
        String configPath = System.getProperty("miniverse.session.config", "");
        if (configPath.isBlank()) {
            return config;
        }

        Path path = Paths.get(configPath);
        config = SessionConfigJson.readRuntimeProperties(path);
        return config;
    }

    private static Optional<String> optionalString(String key) {
        String value = getConfig().getProperty(key, "");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
