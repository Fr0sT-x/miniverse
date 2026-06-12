package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniverseFileUtils;
import dev.frost.miniverse.common.MiniversePaths;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class WorkingDirectorySetup {

    public Path prepareWorkingDirectory(GameSession session, SessionGroup group, String directorySuffix) throws IOException {
        Path sessionsRoot = MiniversePaths.sessionsRoot();
        String groupFolder = this.sanitize(group.getGroupLabel());
        if (directorySuffix != null && !directorySuffix.isBlank()) {
            groupFolder += "_" + this.sanitize(directorySuffix);
        }
        Path sessionDirectory = sessionsRoot.resolve(session.getSessionId()).resolve(groupFolder);

        if (Files.exists(sessionDirectory)) {
            try {
                MiniverseFileUtils.deleteRecursively(sessionDirectory);
            } catch (IOException e) {
                String fallbackName = groupFolder + "_" + System.currentTimeMillis();
                Path fallback = sessionsRoot.resolve(session.getSessionId()).resolve(fallbackName);
                Miniverse.LOGGER.warn("Failed to clear session dir {}; using {} instead", sessionDirectory, fallback, e);
                sessionDirectory = fallback;
            }
        }

        Files.createDirectories(sessionDirectory);
        Files.createDirectories(sessionDirectory.resolve("logs"));
        return sessionDirectory;
    }

    public Path prepareInspectionDirectory(String sessionId) throws IOException {
        Path inspectionsRoot = MiniversePaths.runRoot().resolve("miniverse").resolve("session-inspections");
        Files.createDirectories(inspectionsRoot);
        Path directory = inspectionsRoot.resolve(this.sanitize(sessionId) + "_" + System.currentTimeMillis());
        Files.createDirectories(directory);
        Files.createDirectories(directory.resolve("logs"));
        return directory;
    }

    public Path prepareMapEditorDirectory(String mapId) throws IOException {
        Path root = MiniversePaths.runRoot().resolve("miniverse").resolve("map-editing");
        Path directory = root.resolve(this.sanitize(mapId) + "_" + System.currentTimeMillis());
        Files.createDirectories(directory.resolve("logs"));
        return directory;
    }

    public void replaceRuntimeSymlinks(Path workingDirectory, Path serverRoot) throws IOException {
        this.deleteRuntimeEntry(workingDirectory.resolve("fabric-server-launch.jar"));
        this.deleteRuntimeEntry(workingDirectory.resolve("server.jar"));
        this.deleteRuntimeEntry(workingDirectory.resolve("libraries"));
        this.deleteRuntimeEntry(workingDirectory.resolve("mods"));
        this.deleteRuntimeEntry(workingDirectory.resolve("config"));
        this.deleteRuntimeEntry(workingDirectory.resolve("versions"));
        this.createRuntimeSymlinks(workingDirectory, serverRoot);
    }

    private void deleteRuntimeEntry(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            MiniverseFileUtils.deleteRecursively(path);
        }
    }

    public void createRuntimeSymlinks(Path workingDirectory, Path serverRoot) throws IOException {
        Miniverse.LOGGER.info("Creating runtime links for session in {}", workingDirectory);
        this.linkFile(workingDirectory.resolve("fabric-server-launch.jar"), serverRoot.resolve("fabric-server-launch.jar"));
        this.linkFile(workingDirectory.resolve("server.jar"), serverRoot.resolve("server.jar"));
        this.linkDirectory(workingDirectory.resolve("libraries"), serverRoot.resolve("libraries"));
        this.linkDirectory(workingDirectory.resolve("mods"), serverRoot.resolve("mods"));
        this.linkDirectory(workingDirectory.resolve("config"), serverRoot.resolve("config"));
        this.linkOptionalDirectory(workingDirectory.resolve("versions"), serverRoot.resolve("versions"));
    }

    private void linkFile(Path link, Path target) throws IOException {
        if (this.isWindows()) {
            try {
                Files.createLink(link, target);
            } catch (IOException e) {
                Miniverse.LOGGER.warn("Failed to create hard link for {}, falling back to copy: {}", link.getFileName(), e.getMessage());
                Files.copy(target, link);
            }
        } else {
            try {
                Files.createSymbolicLink(link, target);
            } catch (IOException e) {
                Miniverse.LOGGER.warn("Failed to create symbolic link for {}, falling back to hard link: {}", link.getFileName(), e.getMessage());
                try {
                    Files.createLink(link, target);
                } catch (IOException ex) {
                    Files.copy(target, link);
                }
            }
        }
    }

    private void linkDirectory(Path link, Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            throw new IOException("Runtime directory is missing: " + target);
        }

        if (this.isWindows()) {
            try {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toAbsolutePath().toString(), target.toAbsolutePath().toString());
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IOException("mklink /J exited with code " + exitCode);
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IOException("Failed to create directory junction for " + link.getFileName(), e);
            }
        } else {
            Files.createSymbolicLink(link, target);
        }
    }

    private void linkOptionalDirectory(Path link, Path target) throws IOException {
        if (Files.isDirectory(target)) {
            this.linkDirectory(link, target);
        }
    }

    public Path detectServerRoot() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            Path devRuntime = MiniversePaths.serverRuntimeRoot();
            Miniverse.LOGGER.info("Development mode detected. Targeting dedicated runtime: {}", devRuntime);
            if (!Files.exists(devRuntime.resolve("fabric-server-launch.jar"))) {
                Miniverse.LOGGER.error("====================================================");
                Miniverse.LOGGER.error("MISSING DEVELOPMENT RUNTIME!");
                Miniverse.LOGGER.error("You must create a real Fabric server runtime in:");
                Miniverse.LOGGER.error(devRuntime.toString());
                Miniverse.LOGGER.error("This is required to launch backend sessions in dev.");
                Miniverse.LOGGER.error("Preferred dev path is <project>/server-runtime. Existing legacy path is also supported at <project>/run/server-runtime.");
                Miniverse.LOGGER.error("You can override it with -Dminiverse.serverRuntime=<path> or MINIVERSE_SERVER_RUNTIME.");
                Miniverse.LOGGER.error("====================================================");
                throw new IllegalStateException("Missing development runtime at " + devRuntime);
            }
            return devRuntime;
        }

        Path cwd = Paths.get("").toAbsolutePath();
        Path current = cwd;
        while (current != null) {
            if (Files.exists(current.resolve("fabric-server-launch.jar")) &&
                    Files.isDirectory(current.resolve("mods"))) {
                Miniverse.LOGGER.info("Production mode detected. Targeting shared server root: {}", current);
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not detect server root directory (fabric-server-launch.jar and mods/ required)");
    }

    public void validateSharedRuntime(Path serverRoot) throws IOException {
        List<String> missing = new ArrayList<>();
        if (!Files.exists(serverRoot.resolve("fabric-server-launch.jar"))) {
            missing.add("fabric-server-launch.jar");
        }
        if (!Files.exists(serverRoot.resolve("server.jar"))) {
            missing.add("server.jar");
        }
        if (!Files.isDirectory(serverRoot.resolve("libraries"))) {
            missing.add("libraries/");
        }
        if (!Files.isDirectory(serverRoot.resolve("mods"))) {
            missing.add("mods/");
        }

        if (!missing.isEmpty()) {
            throw new IOException("Shared server runtime is missing required files/directories: " + String.join(", ", missing));
        }

        this.validateFabricLauncherClasspath(serverRoot);
        
        if (!Files.isDirectory(serverRoot.resolve("config"))) {
            Files.createDirectories(serverRoot.resolve("config"));
        }

        Miniverse.LOGGER.info("Shared runtime integrity check passed at {}", serverRoot);
    }

    private void validateFabricLauncherClasspath(Path serverRoot) throws IOException {
        Path launcherJar = serverRoot.resolve("fabric-server-launch.jar");
        List<String> missing = new ArrayList<>();

        try (JarFile jar = new JarFile(launcherJar.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                throw new IOException("fabric-server-launch.jar is missing META-INF/MANIFEST.MF");
            }

            Attributes attributes = manifest.getMainAttributes();
            String mainClass = attributes.getValue(Attributes.Name.MAIN_CLASS);
            if (mainClass == null || mainClass.isBlank()) {
                throw new IOException("fabric-server-launch.jar manifest is missing Main-Class");
            }

            String classPath = attributes.getValue(Attributes.Name.CLASS_PATH);
            if (classPath == null || classPath.isBlank()) {
                throw new IOException("fabric-server-launch.jar manifest is missing Class-Path");
            }

            for (String entry : classPath.trim().split("\\s+")) {
                Path dependency = serverRoot.resolve(entry.replace('/', java.io.File.separatorChar)).normalize();
                if (!dependency.startsWith(serverRoot.normalize()) || !Files.exists(dependency)) {
                    missing.add(entry);
                }
            }
        }

        if (!missing.isEmpty()) {
            throw new IOException(
                "Shared server runtime is incomplete for the current Fabric launcher. Missing launcher classpath entries: "
                    + String.join(", ", missing)
                    + ". Reinstall the dev runtime for Minecraft "
                    + FabricLoader.getInstance().getModContainer("minecraft").map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("the configured version")
                    + " and Fabric Loader "
                    + FabricLoader.getInstance().getModContainer("fabricloader").map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("the configured version")
                    + "."
            );
        }
    }

    public void buildLatestModJar() throws IOException {
        Path projectRoot = MiniversePaths.projectRoot();
        Path gradleWrapper = projectRoot.resolve(this.isWindows() ? "gradlew.bat" : "gradlew");
        if (!Files.isRegularFile(gradleWrapper)) {
            throw new IOException("Cannot build latest Miniverse jar: missing Gradle wrapper at " + gradleWrapper);
        }

        List<String> command = List.of(gradleWrapper.toString(), "remapJar", "--no-daemon");
        Miniverse.LOGGER.info("Building latest Miniverse backend jar: {}", String.join(" ", command));

        Path buildLog = MiniversePaths.runRoot().resolve("logs").resolve("miniverse-backend-build.log");
        Files.createDirectories(buildLog.getParent());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(projectRoot.toFile());
        builder.redirectOutput(buildLog.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        try {
            if (!process.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Gradle remapJar timed out after 180 seconds. See " + buildLog);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Gradle remapJar failed with exit code " + exitCode + ". See " + buildLog);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while building latest Miniverse jar", e);
        }
    }

    public void syncLatestModJar(Path serverRoot) throws IOException {
        Path buildLibs = MiniversePaths.projectRoot().resolve("build").resolve("libs");
        if (!Files.exists(buildLibs)) {
            throw new IOException("Cannot sync latest Miniverse jar: build/libs does not exist");
        }

        Path latestJar = null;
        long latestTime = 0;

        try (var stream = Files.list(buildLibs)) {
            for (Path file : stream.toList()) {
                if (!this.isCandidateModJar(file)) {
                    continue;
                }
                long time = Files.getLastModifiedTime(file).toMillis();
                if (time > latestTime) {
                    latestTime = time;
                    latestJar = file;
                }
            }
        }

        if (latestJar == null) {
            throw new IOException("Cannot sync latest Miniverse jar: no runnable mod jar found in " + buildLibs);
        }

        Path targetMods = serverRoot.resolve("mods");
        Files.createDirectories(targetMods);
        this.deleteExistingMiniverseJars(targetMods);

        Path targetJar = targetMods.resolve(latestJar.getFileName().toString());
        Miniverse.LOGGER.info("Syncing latest Miniverse mod build to dev runtime: {}", latestJar.getFileName());
        Files.copy(latestJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean isCandidateModJar(Path file) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file)
            && name.endsWith(".jar")
            && !name.endsWith("-sources.jar")
            && !name.endsWith("-javadoc.jar")
            && this.isMiniverseModJar(file);
    }

    private void deleteExistingMiniverseJars(Path modsDirectory) throws IOException {
        try (var stream = Files.list(modsDirectory)) {
            for (Path file : stream.toList()) {
                if (this.isCandidateModJar(file)) {
                    Miniverse.LOGGER.info("Removing previous Miniverse runtime jar: {}", file.getFileName());
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private boolean isMiniverseModJar(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getEntry("fabric.mod.json");
            if (entry == null) {
                return false;
            }

            try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                return json.has("id") && "miniverse".equals(json.get("id").getAsString());
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    public String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
