package dev.frost.miniverse.common;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class MiniversePaths {
    private static volatile Path projectRoot;

    private MiniversePaths() {
    }

    public static Path projectRoot() {
        Path cached = projectRoot;
        if (cached != null) {
            return cached;
        }

        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            Path fallback = Paths.get("").toAbsolutePath();
            projectRoot = fallback;
            return fallback;
        }

        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("gradle.properties"))) {
                projectRoot = current;
                return current;
            }
            current = current.getParent();
        }

        Path fallback = Paths.get("").toAbsolutePath();
        projectRoot = fallback;
        return fallback;
    }

    public static Path runRoot() {
        String override = System.getProperty("miniverse.runRoot", System.getenv("MINIVERSE_RUN_ROOT"));
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath().normalize();
        }
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return projectRoot();
        }
        return projectRoot().resolve("run");
    }

    public static Path serverRuntimeRoot() {
        String override = System.getProperty("miniverse.serverRuntime", System.getenv("MINIVERSE_SERVER_RUNTIME"));
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath().normalize();
        }

        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return projectRoot();
        }

        Path preferred = projectRoot().resolve("server-runtime");
        if (Files.exists(preferred)) {
            return preferred;
        }

        Path legacy = runRoot().resolve("server-runtime");
        if (Files.exists(legacy)) {
            return legacy;
        }
        return preferred;
    }

    public static Path sessionsRoot() {
        return runRoot().resolve("miniverse").resolve("sessions");
    }

    public static Path mapsRoot() {
        Path mainSessionsRoot = dev.frost.miniverse.session.SessionRuntimeConfig.getMainSessionsRoot().orElse(null);
        if (mainSessionsRoot != null && mainSessionsRoot.getParent() != null) {
            return mainSessionsRoot.getParent().resolve("maps").toAbsolutePath().normalize();
        }
        return runRoot().resolve("miniverse").resolve("maps");
    }

    public static Path profilesRoot() {
        Path mainSessionsRoot = dev.frost.miniverse.session.SessionRuntimeConfig.getMainSessionsRoot().orElse(null);
        if (mainSessionsRoot != null && mainSessionsRoot.getParent() != null) {
            return mainSessionsRoot.getParent().resolve("profiles").toAbsolutePath().normalize();
        }
        return runRoot().resolve("miniverse").resolve("profiles");
    }

    public static Path mainServerProperties() {
        return runRoot().resolve("server.properties");
    }

    public static Path fabricLaunchConfig() {
        return projectRoot().resolve(".gradle").resolve("loom-cache").resolve("launch.cfg");
    }

    public static Path miniverseConfig(String fileName) {
        return runRoot().resolve("config").resolve("miniverse").resolve(fileName);
    }
}
