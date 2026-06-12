package dev.frost.miniverse.common;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class MiniverseFileUtils {
    private MiniverseFileUtils() {
    }

    public static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        if (isDirectoryLink(root)) {
            deletePath(root);
            return;
        }

        BasicFileAttributes attrs = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attrs.isDirectory()) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
                for (Path entry : entries) {
                    deleteRecursively(entry);
                }
            }
        }

        deletePath(root);
    }

    public static boolean isDirectoryLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            return true;
        }

        if (!isWindows() || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        Path parent = path.getParent();
        if (parent == null) {
            return false;
        }

        try {
            Path expectedRealPath = parent.toRealPath().resolve(path.getFileName());
            Path actualRealPath = path.toRealPath();
            return !expectedRealPath.toString().equalsIgnoreCase(actualRealPath.toString());
        } catch (IOException ignored) {
            return false;
        }
    }

    public static long lastModifiedMillis(Path root) {
        if (!Files.exists(root)) {
            return 0L;
        }

        try {
            return lastModifiedMillis(root, 0);
        } catch (IOException e) {
            try {
                return Files.getLastModifiedTime(root).toMillis();
            } catch (IOException ignored) {
                return 0L;
            }
        }
    }

    private static long lastModifiedMillis(Path path, int depth) throws IOException {
        long latest = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
        if (depth > 8 || isDirectoryLink(path)) {
            return latest;
        }

        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isDirectory()) {
            return latest;
        }

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
            for (Path entry : entries) {
                latest = Math.max(latest, lastModifiedMillis(entry, depth + 1));
            }
        }
        return latest;
    }

    private static void deletePath(Path path) throws IOException {
        boolean deleted = path.toFile().delete();
        if (!deleted) {
            Files.deleteIfExists(path);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
