package dev.frost.miniverse.common;

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
        return projectRoot().resolve("run");
    }

    public static Path sessionsRoot() {
        return runRoot().resolve("sessions");
    }

    public static Path mainServerProperties() {
        return runRoot().resolve("server.properties");
    }

    public static Path fabricLaunchConfig() {
        return projectRoot().resolve(".gradle").resolve("loom-cache").resolve("launch.cfg");
    }

    public static Path miniverseConfig(String fileName) {
        return projectRoot().resolve("config").resolve("miniverse").resolve(fileName);
    }
}
