package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

public final class ServerLauncher {

    public record LaunchResult(SessionGroup group, Process process, int port, Path workingDirectory) {
    }

    public LaunchResult launch(GameSession session, SessionGroup group) throws IOException {
        // 1. Detect and validate the shared server runtime
        Path serverRoot = detectServerRoot();
        validateSharedRuntime(serverRoot);

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            this.syncLatestModJar(serverRoot);
        }

        // 2. Prepare an isolated working directory for the session
        Path workingDirectory = this.prepareWorkingDirectory(session, group);
        int port = this.reservePort();

        // 3. Create symbolic links/junctions to the shared runtime files
        this.createRuntimeSymlinks(workingDirectory, serverRoot);

        // 4. Write session-specific configuration files into the working directory
        this.writeEula(workingDirectory);
        this.writeSessionConfig(workingDirectory, session, group);
        this.writeServerProperties(workingDirectory, session, group, port);

        group.markLaunching(workingDirectory, port);

        // 5. Build the launch command to run from the isolated directory
        String javaExecutable = this.resolveJavaExecutable();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.add("-Xmx2G");
        command.add("-Dminiverse.session.config=" + workingDirectory.resolve("miniverse-session.json").toAbsolutePath());
        command.add("-Dminiverse.session.game=" + session.getGameType().getCommandName());
        command.add("-jar");
        command.add("fabric-server-launch.jar"); // Relative path works due to the symlink/hardlink
        command.add("nogui");

        Miniverse.LOGGER.info("Backend launch command: {}", String.join(" ", command));

        long startTime = System.currentTimeMillis();

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile()); // Execute from the isolated session directory
        builder.redirectOutput(workingDirectory.resolve("stdout.log").toFile());
        builder.redirectError(workingDirectory.resolve("stderr.log").toFile());

        Process process = builder.start();

        // 6. Wait for the server to boot and mark it as running
        boolean booted = waitForServerBoot(workingDirectory.resolve("stdout.log"), process);
        if (!booted) {
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            throw new IOException("Server failed to boot. Exit code: " + exitCode);
        }

        long bootTime = System.currentTimeMillis() - startTime;
        String address = "127.0.0.1:" + port;
        group.markRunning(process, address);

        Miniverse.LOGGER.info("Launched {} session {} for {} at {} (Boot time: {}ms)", session.getGameType().getDisplayName(), session.getSessionId(), group.getDisplayName(), address, bootTime);
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
            } catch (IOException ignored) {
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Creates a clean, isolated working directory for a new session group.
     */
    private Path prepareWorkingDirectory(GameSession session, SessionGroup group) throws IOException {
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
        Files.createDirectories(sessionDirectory.resolve("logs"));
        return sessionDirectory;
    }

    /**
     * Creates symbolic links (or junctions/hard links on Windows) pointing to the shared server runtime files.
     */
    private void createRuntimeSymlinks(Path workingDirectory, Path serverRoot) throws IOException {
        Miniverse.LOGGER.info("Creating runtime links for session in {}", workingDirectory);
        this.linkFile(workingDirectory.resolve("fabric-server-launch.jar"), serverRoot.resolve("fabric-server-launch.jar"));
        this.linkFile(workingDirectory.resolve("server.jar"), serverRoot.resolve("server.jar"));
        this.linkDirectory(workingDirectory.resolve("libraries"), serverRoot.resolve("libraries"));
        this.linkDirectory(workingDirectory.resolve("mods"), serverRoot.resolve("mods"));
        this.linkDirectory(workingDirectory.resolve("config"), serverRoot.resolve("config"));
    }

    private void linkFile(Path link, Path target) throws IOException {
        if (this.isWindows()) {
            try {
                // Windows hard links do not require admin privileges
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
        if (this.isWindows()) {
            try {
                // Windows directory junctions do not require admin privileges
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

    private void syncLatestModJar(Path serverRoot) {
        Path buildLibs = MiniversePaths.projectRoot().resolve("build").resolve("libs");
        if (!Files.exists(buildLibs)) {
            return;
        }

        try {
            Path latestJar = null;
            long latestTime = 0;

            try (var stream = Files.list(buildLibs)) {
                for (Path file : stream.toList()) {
                    if (file.toString().endsWith(".jar") && !file.toString().endsWith("-sources.jar") && !file.toString().endsWith("-javadoc.jar")) {
                        long time = Files.getLastModifiedTime(file).toMillis();
                        if (time > latestTime) {
                            latestTime = time;
                            latestJar = file;
                        }
                    }
                }
            }

            if (latestJar != null) {
                Path targetMods = serverRoot.resolve("mods");
                Files.createDirectories(targetMods);
                Path targetJar = targetMods.resolve(latestJar.getFileName().toString());

                if (!Files.exists(targetJar) || Files.getLastModifiedTime(targetJar).toMillis() < latestTime) {
                    Miniverse.LOGGER.info("Syncing latest mod build to dev runtime: {}", latestJar.getFileName());
                    Files.copy(latestJar, targetJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to sync latest mod jar to dev runtime", e);
        }
    }

    /**
     * Detects the main server root directory by searching for key Fabric server files.
     */
    private Path detectServerRoot() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            Path devRuntime = MiniversePaths.serverRuntimeRoot();
            Miniverse.LOGGER.info("Development mode detected. Targeting dedicated runtime: {}", devRuntime);
            if (!Files.exists(devRuntime.resolve("fabric-server-launch.jar"))) {
                Miniverse.LOGGER.error("====================================================");
                Miniverse.LOGGER.error("MISSING DEVELOPMENT RUNTIME!");
                Miniverse.LOGGER.error("You must create a real Fabric server runtime in:");
                Miniverse.LOGGER.error(devRuntime.toString());
                Miniverse.LOGGER.error("This is required to launch backend sessions in dev.");
                Miniverse.LOGGER.error("Run the Fabric server installer targeting this folder.");
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

    /**
     * Checks that the shared runtime contains all required files/folders.
     */
    private void validateSharedRuntime(Path serverRoot) throws IOException {
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
        
        // Ensure config exists but don't fail if it doesn't, just create it
        if (!Files.isDirectory(serverRoot.resolve("config"))) {
            Files.createDirectories(serverRoot.resolve("config"));
        }

        Miniverse.LOGGER.info("Shared runtime integrity check passed at {}", serverRoot);
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