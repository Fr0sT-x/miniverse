package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

public final class SessionRuntimeConfig {
    private static Properties config;

    private SessionRuntimeConfig() {
    }

    public static synchronized Optional<String> getSessionId() {
        String sessionId = getConfig().getProperty("sessionId", "");
        return sessionId.isBlank() ? Optional.empty() : Optional.of(sessionId);
    }

    public static synchronized String getReturnHost() {
        return getConfig().getProperty("return.host", "127.0.0.1");
    }

    public static synchronized int getReturnPort() {
        String value = getConfig().getProperty("return.port", "25565");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 25565;
        }
    }

    public static synchronized boolean isSessionServer() {
        return getSessionId().isPresent();
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
        if (!Files.exists(path)) {
            return config;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            config.load(reader);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Could not load Miniverse session runtime config {}", configPath, e);
        }
        return config;
    }
}
