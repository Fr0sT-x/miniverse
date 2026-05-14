package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import net.minecraft.nbt.NbtCompound;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Stream;

public final class ServerLauncher {

    // Template cache location: miniverse/runtime-template/
    private static final String TEMPLATE_FOLDER = "miniverse/runtime-template";
    private static final Set<String> TEMPLATE_INCLUDE = Set.of(
            "fabric-server-launch.jar", "server.jar", "libraries", "mods", "config"
    );
    private static final Set<String> TEMPLATE_EXCLUDE = Set.of(
            "world", "logs", "crash-reports", "sessions", "run", "usercache.json",
            "banned-ips.json", "banned-players.json", "ops.json", "whitelist.json"
    );
    public record LaunchResult(SessionGroup group, Process process, int port, Path workingDirectory) {
    }

    public LaunchResult launch(GameSession session, SessionGroup group) throws IOException {
        // 1. Detect server root
        Path serverRoot = detectServerRoot();
        // 2. Ensure template exists
        Path templateDir = serverRoot.resolve(TEMPLATE_FOLDER);
        ensureTemplateExists(serverRoot, templateDir);
        int port = this.reservePort();
        Path workingDirectory = this.prepareWorkingDirectory(session, group, templateDir);
        this.writeEula(workingDirectory);
        this.writeSessionConfig(workingDirectory, session, group);
        this.writeServerProperties(workingDirectory, session, group, port);

        group.markLaunching(workingDirectory, port);

        // Ensure required files exist
        Path fabricLauncherJar = workingDirectory.resolve("fabric-server-launch.jar");
        if (!Files.exists(fabricLauncherJar)) {
            throw new IOException("Missing fabric-server-launch.jar in session directory: " + fabricLauncherJar);
        }
        if (!Files.isDirectory(workingDirectory.resolve("libraries"))) {
            throw new IOException("Missing libraries/ directory in session directory: " + workingDirectory);
        }
        if (!Files.isDirectory(workingDirectory.resolve("mods"))) {
            throw new IOException("Missing mods/ directory in session directory: " + workingDirectory);
        }
        if (!Files.isDirectory(workingDirectory.resolve("config"))) {
            throw new IOException("Missing config/ directory in session directory: " + workingDirectory);
        }
        if (!Files.exists(workingDirectory.resolve("server.properties"))) {
            throw new IOException("Missing server.properties in session directory: " + workingDirectory);
        }

        // Build production-safe command
        String javaExecutable = this.resolveJavaExecutable();
        List<String> command = List.of(
            javaExecutable,
            "-Xmx2G",
            "-jar",
            "fabric-server-launch.jar",
            "nogui"
        );

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectOutput(workingDirectory.resolve("stdout.log").toFile());
        builder.redirectError(workingDirectory.resolve("stderr.log").toFile());

        Process process = builder.start();

        // Wait for server to fully boot (look for "Done" in stdout)
        boolean booted = waitForServerBoot(workingDirectory.resolve("stdout.log"), process);
        if (!booted) {
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            throw new IOException("Server failed to boot. Exit code: " + exitCode);
        }
        String address = "127.0.0.1:" + port;
        group.markRunning(process, address);

        Miniverse.LOGGER.info("Launched {} session {} for {} at {}", session.getGameType().getDisplayName(), session.getSessionId(), group.getDisplayName(), address);
        return new LaunchResult(group, process, port, workingDirectory);
    }

    public void stop(SessionGroup group) {
        Process process = group.getProcess();
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                try {
                    process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        group.markStopped();
    }

    /**
     * Waits for the Fabric server to fully boot by monitoring the stdout log for the "Done" message.
     * Returns true if the server boots successfully, false otherwise.
     */
    private boolean waitForServerBoot(Path stdoutLog, Process process) {
        long deadline = System.currentTimeMillis() + 120_000L; // 2 minutes
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                return false;
            }
            try {
                if (Files.exists(stdoutLog)) {
                    List<String> lines = Files.readAllLines(stdoutLog);
                    for (String line : lines) {
                        if (line.contains("Done") && line.contains("For help, type")) {
                            return true;
                        }
                    }
                }
            } catch (IOException ignored) {}
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private Path prepareWorkingDirectory(GameSession session, SessionGroup group, Path templateDir) throws IOException {
        Path sessionsRoot = MiniversePaths.sessionsRoot();
        String groupFolder = this.sanitize(group.getGroupLabel());
        Path sessionDirectory = sessionsRoot.resolve(session.getSessionId()).resolve(groupFolder);
        if (Files.exists(sessionDirectory)) {
            try {
                deleteRecursively(sessionDirectory);
            } catch (IOException e) {
                String fallbackName = groupFolder + "_" + System.currentTimeMillis();
                Path fallback = sessionsRoot.resolve(session.getSessionId()).resolve(fallbackName);
                Miniverse.LOGGER.warn("Failed to clear session dir {}; using {} instead", sessionDirectory, fallback, e);
                sessionDirectory = fallback;
            }
        }
        Files.createDirectories(sessionDirectory);
        long start = System.currentTimeMillis();
        copyTemplateToSession(templateDir, sessionDirectory);
        long duration = System.currentTimeMillis() - start;
        Miniverse.LOGGER.info("Copied session template to {} in {} ms", sessionDirectory, duration);
        Files.createDirectories(sessionDirectory.resolve("logs"));
        return sessionDirectory;
    }

    /**
     * Detects the main server root directory by searching for fabric-server-launch.jar and mods/.
     */
    private Path detectServerRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path current = cwd;
        while (current != null) {
            if (Files.exists(current.resolve("fabric-server-launch.jar")) &&
                Files.isDirectory(current.resolve("mods"))) {
                Miniverse.LOGGER.info("Detected server root: {}", current);
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not detect server root directory (fabric-server-launch.jar and mods/ required)");
    }

    /**
     * Ensures the template exists, creates it if missing, and checks integrity.
     */
    private void ensureTemplateExists(Path serverRoot, Path templateDir) throws IOException {
        if (Files.exists(templateDir)) {
            Miniverse.LOGGER.info("Miniverse session template found at {}. Reusing.", templateDir);
            checkTemplateIntegrity(templateDir);
            return;
        }
        Miniverse.LOGGER.info("Miniverse session template not found. Creating at {}...", templateDir);
        createTemplate(serverRoot, templateDir);
        checkTemplateIntegrity(templateDir);
    }

    /**
     * Creates the session template by copying only required files/folders.
     */
    private void createTemplate(Path serverRoot, Path templateDir) throws IOException {
        Files.createDirectories(templateDir);
        // Always try to copy both fabric-server-launch.jar and server.jar if present
        String[] jars = {"fabric-server-launch.jar", "server.jar"};
        for (String jar : jars) {
            Path src = serverRoot.resolve(jar);
            Path dest = templateDir.resolve(jar);
            if (Files.exists(src)) {
                Miniverse.LOGGER.info("Copying file to template: {}", jar);
                Files.copy(src, dest);
            } else {
                Miniverse.LOGGER.warn("Template source missing: {}", src);
            }
        }
        // Copy other included directories
        for (String entry : TEMPLATE_INCLUDE) {
            if (entry.equals("fabric-server-launch.jar") || entry.equals("server.jar")) continue;
            Path src = serverRoot.resolve(entry);
            Path dest = templateDir.resolve(entry);
            if (Files.isDirectory(src)) {
                Miniverse.LOGGER.info("Copying directory to template: {}", entry);
                copyDirectoryFiltered(src, dest, TEMPLATE_EXCLUDE);
            } else if (Files.exists(src)) {
                Miniverse.LOGGER.info("Copying file to template: {}", entry);
                Files.copy(src, dest);
            } else {
                Miniverse.LOGGER.warn("Template source missing: {}", src);
            }
        }
    }

    /**
     * Copies the template to a new session directory, efficiently.
     */
    private void copyTemplateToSession(Path templateDir, Path sessionDir) throws IOException {
        try (Stream<Path> stream = Files.walk(templateDir)) {
            stream.forEach(source -> {
                try {
                    Path relative = templateDir.relativize(source);
                    Path target = sessionDir.resolve(relative);
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(source, target);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    /**
     * Checks that the template contains all required files/folders.
     */
    private void checkTemplateIntegrity(Path templateDir) throws IOException {
        Path fabricJar = templateDir.resolve("fabric-server-launch.jar");
        Path serverJar = templateDir.resolve("server.jar");
        Path mods = templateDir.resolve("mods");
        Path libs = templateDir.resolve("libraries");
        boolean hasFabric = Files.exists(fabricJar);
        boolean hasServer = Files.exists(serverJar);
        if (!hasFabric && !hasServer) {
            throw new IOException("Template missing both fabric-server-launch.jar and server.jar: " + fabricJar + ", " + serverJar);
        }
        if (!hasFabric) {
            Miniverse.LOGGER.warn("Template missing fabric-server-launch.jar: {}", fabricJar);
        }
        if (!hasServer) {
            Miniverse.LOGGER.warn("Template missing server.jar: {}", serverJar);
        }
        if (!Files.isDirectory(mods)) {
            throw new IOException("Template missing mods/ directory: " + mods);
        }
        if (!Files.isDirectory(libs)) {
            throw new IOException("Template missing libraries/ directory: " + libs);
        }
        Miniverse.LOGGER.info("Template integrity check passed at {}", templateDir);
    }

    /**
     * Recursively copies a directory, excluding any entries in the exclude set.
     */
    private void copyDirectoryFiltered(Path src, Path dest, Set<String> exclude) throws IOException {
        Files.walk(src).forEach(source -> {
            try {
                Path relative = src.relativize(source);
                String first = relative.getNameCount() > 0 ? relative.getName(0).toString() : "";
                if (exclude.contains(first)) {
                    Miniverse.LOGGER.info("Excluding from template: {}", source);
                    return;
                }
                Path target = dest.resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void writeEula(Path workingDirectory) throws IOException {
        Path eula = workingDirectory.resolve("eula.txt");
        Files.writeString(eula, "eula=true\n", java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeSessionConfig(Path workingDirectory, GameSession session, SessionGroup group) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("game", session.getGameType().getCommandName());
        properties.setProperty("sessionId", session.getSessionId());
        properties.setProperty("groupLabel", group.getGroupLabel());
        properties.setProperty("assignmentLabel", group.getGroupLabel());
        properties.setProperty("return.host", this.resolveReturnHost());
        properties.setProperty("return.port", Integer.toString(this.resolveReturnPort()));

        NbtCompound settings = session.getSettings();
        Properties settingsProperties = new Properties();
        this.definitionFor(session).ifPresent(definition -> definition.writeSessionProperties(settings, settingsProperties));
        properties.putAll(settingsProperties);

        for (SessionGroup sessionGroup : groupsForConfig(session, group)) {
            String teamLabel = sessionGroup.getGroupLabel();
            for (UUID playerUuid : sessionGroup.getPlayerUuids()) {
                properties.setProperty("player." + playerUuid, "true");
                properties.setProperty("player." + playerUuid + ".team", teamLabel);
            }
        }

        SessionConfigJson.write(
            workingDirectory.resolve("miniverse-session.json"),
            SessionConfigJson.runtimeSession(session, group, groupsForConfig(session, group), settingsProperties, properties.getProperty("return.host"), this.parsePort(properties.getProperty("return.port"), 25565))
        );
    }

    private void writeServerProperties(Path workingDirectory, GameSession session, SessionGroup group, int port) throws IOException {
        Properties properties = new Properties();
        SessionServerConfig serverConfig = SessionServerConfig.getInstance();
        properties.setProperty("accepts-transfers", Boolean.toString(serverConfig.acceptsTransfers()));
        properties.setProperty("allow-flight", Boolean.toString(serverConfig.allowFlight()));
        properties.setProperty("allow-nether", "true");
        properties.setProperty("broadcast-console-to-ops", "true");
        properties.setProperty("broadcast-rcon-to-ops", "true");
        properties.setProperty("difficulty", serverConfig.difficulty());
        properties.setProperty("enable-command-block", "true");
        properties.setProperty("enable-jmx-monitoring", "false");
        properties.setProperty("enable-rcon", "false");
        properties.setProperty("enable-status", "true");
        properties.setProperty("enforce-secure-profile", "false");
        properties.setProperty("force-gamemode", "false");
        properties.setProperty("function-permission-level", "2");
        properties.setProperty("gamemode", "survival");
        properties.setProperty("generate-structures", "true");
        properties.setProperty("generator-settings", "{}");
        properties.setProperty("hardcore", "false");
        properties.setProperty("level-name", "world");
        properties.setProperty("level-seed", Long.toString(session.getSeedPlan().sharedSeed()));
        properties.setProperty("level-type", "minecraft:normal");
        int maxPlayers = groupsForConfig(session, group).stream()
            .mapToInt(SessionGroup::getPlayerCount)
            .sum();
        properties.setProperty("max-players", Integer.toString(Math.max(1, maxPlayers)));
        properties.setProperty("motd", session.getGameType().getDisplayName() + " " + session.getSessionId() + " / " + group.getGroupLabel());
        properties.setProperty("network-compression-threshold", "256");
        properties.setProperty("online-mode", Boolean.toString(serverConfig.onlineMode()));
        properties.setProperty("op-permission-level", "4");
        properties.setProperty("player-idle-timeout", "0");
        properties.setProperty("prevent-proxy-connections", "false");
        properties.setProperty("pvp", "true");
        properties.setProperty("query.port", Integer.toString(port));
        properties.setProperty("rate-limit", "0");
        properties.setProperty("rcon.password", "");
        properties.setProperty("rcon.port", "25575");
        properties.setProperty("require-resource-pack", "false");
        properties.setProperty("resource-pack", "");
        properties.setProperty("resource-pack-prompt", "");
        properties.setProperty("server-ip", "");
        properties.setProperty("server-port", Integer.toString(port));
        properties.setProperty("simulation-distance", Integer.toString(serverConfig.simulationDistance()));
        properties.setProperty("spawn-monsters", "true");
        properties.setProperty("spawn-protection", Integer.toString(serverConfig.spawnProtection()));
        properties.setProperty("sync-chunk-writes", "true");
        properties.setProperty("text-filtering-config", "");
        properties.setProperty("use-native-transport", "true");
        properties.setProperty("view-distance", Integer.toString(serverConfig.viewDistance()));
        properties.setProperty("white-list", "false");

        StringBuilder content = new StringBuilder();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            content.append(key).append('=').append(properties.getProperty(key)).append('\n');
        }
        Files.writeString(workingDirectory.resolve("server.properties"), content.toString(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String resolveJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        Path javaBin = Paths.get(javaHome, "bin", this.isWindows() ? "java.exe" : "java");
        return javaBin.toString();
    }

    private List<SessionGroup> groupsForConfig(GameSession session, SessionGroup group) {
        if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD) {
            return List.copyOf(session.snapshotGroups());
        }
        return List.of(group);
    }

    private Optional<MinigameDefinition> definitionFor(GameSession session) {
        return Optional.of(session.getGameType().definition());
    }

    private int reservePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Returns the Java host and port configured for returning players.
     * Fallbacks to 127.0.0.1:25565 if not explicitly set.
     */
    private String resolveReturnHost() throws IOException {
        Properties properties = this.readMainServerProperties();
        String serverIp = properties.getProperty("server-ip", "").trim();
        return serverIp.isBlank() ? "127.0.0.1" : serverIp;
    }

    private int resolveReturnPort() throws IOException {
        Properties properties = this.readMainServerProperties();
        String value = properties.getProperty("server-port", "25565").trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 25565;
        }
    }

    private Properties readMainServerProperties() throws IOException {
        Path serverProperties = MiniversePaths.mainServerProperties();
        Properties properties = new Properties();
        if (!Files.exists(serverProperties)) {
            return properties;
        }

        try (java.io.Reader reader = Files.newBufferedReader(serverProperties)) {
            properties.load(reader);
        }
        return properties;
    }

    private int parsePort(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
