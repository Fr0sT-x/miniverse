package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniverseFileUtils;
import dev.frost.miniverse.common.MiniversePaths;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.jar.JarFile;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ServerLauncher {

    public record LaunchResult(SessionGroup group, Process process, int port, Path workingDirectory) {
    }

    public record InspectionLaunchResult(Process process, int port, Path workingDirectory) {
    }

    public LaunchResult launch(GameSession session, SessionGroup group) throws IOException {
        return this.launch(session, group, SessionOperatorSnapshot.empty());
    }

    public LaunchResult launch(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot) throws IOException {
        return this.launch(session, group, operatorSnapshot, "", true);
    }

    public LaunchResult launch(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, String directorySuffix) throws IOException {
        return this.launch(session, group, operatorSnapshot, directorySuffix, true);
    }

    public LaunchResult launch(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, String directorySuffix, boolean syncDevelopmentRuntime) throws IOException {
        // 1. Detect and validate the shared server runtime
        Path serverRoot = detectServerRoot();
        validateSharedRuntime(serverRoot);

        if (syncDevelopmentRuntime && FabricLoader.getInstance().isDevelopmentEnvironment()) {
            this.buildLatestModJar();
            this.syncLatestModJar(serverRoot);
        }

        // 2. Prepare an isolated working directory for the session
        Path workingDirectory = this.prepareWorkingDirectory(session, group, directorySuffix);
        int port = this.reservePort();

        // 3. Create symbolic links/junctions to the shared runtime files
        this.createRuntimeSymlinks(workingDirectory, serverRoot);

        // 4. Write session-specific configuration files into the working directory
        this.writeEula(workingDirectory);
        this.writeSessionConfig(workingDirectory, session, group);
        SessionOperatorSnapshot snapshot = operatorSnapshot == null ? SessionOperatorSnapshot.empty() : operatorSnapshot;
        snapshot.writeOpsJson(workingDirectory);
        this.writeServerProperties(workingDirectory, session, group, port);

        group.markLaunching(workingDirectory, port);

        // 5. Build the launch command to run from the isolated directory
        String javaExecutable = this.resolveJavaExecutable();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        this.addMemoryArguments(command);
        command.add("-Dminiverse.session.config=" + workingDirectory.resolve("miniverse-session.json").toAbsolutePath());
        command.add("-Dminiverse.session.game=" + session.getGameType().getCommandName());
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && SessionPermissions.isDevBypassEnabled()) {
            command.add("-Dminiverse.devSession=true");
            command.add("-Dminiverse.session.devBypass=true");
        }
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
        int publicPort = SessionLauncherConfig.getInstance().publicPortForLocalPort(port);
        String address = SessionServerConfig.getInstance().advertisedHost() + ":" + publicPort;
        group.markRunning(process, address);

        if (publicPort != port) {
            Miniverse.LOGGER.info("Launched {} session {} for {} at {} forwarding to local port {} (Boot time: {}ms)", session.getGameType().getDisplayName(), session.getSessionId(), group.getDisplayName(), address, port, bootTime);
        } else {
            Miniverse.LOGGER.info("Launched {} session {} for {} at {} (Boot time: {}ms)", session.getGameType().getDisplayName(), session.getSessionId(), group.getDisplayName(), address, bootTime);
        }
        return new LaunchResult(group, process, port, workingDirectory);
    }

    public LaunchResult launchFromRetained(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, Path retainedSessionRoot, boolean syncDevelopmentRuntime) throws IOException {
        // 1. Detect and validate the shared server runtime
        Path serverRoot = detectServerRoot();
        validateSharedRuntime(serverRoot);

        if (syncDevelopmentRuntime && FabricLoader.getInstance().isDevelopmentEnvironment()) {
            this.buildLatestModJar();
            this.syncLatestModJar(serverRoot);
        }

        // 2. Prepare an isolated working directory for the session
        Path workingDirectory = this.prepareWorkingDirectory(session, group, "relaunch");
        int port = this.reservePort();

        // 3. Copy retained world state into the new working directory
        Path retainedWorld = this.findRetainedWorld(retainedSessionRoot, group.getGroupLabel())
            .orElseThrow(() -> new IOException("No retained world found for session " + session.getSessionId()));
        this.copyDirectory(retainedWorld, workingDirectory.resolve("world"));

        // 4. Create symbolic links/junctions to the shared runtime files
        this.createRuntimeSymlinks(workingDirectory, serverRoot);

        // 5. Write session-specific configuration files into the working directory
        this.writeEula(workingDirectory);
        this.writeSessionConfig(workingDirectory, session, group);
        SessionOperatorSnapshot snapshot = operatorSnapshot == null ? SessionOperatorSnapshot.empty() : operatorSnapshot;
        snapshot.writeOpsJson(workingDirectory);
        this.writeServerProperties(workingDirectory, session, group, port);

        group.markLaunching(workingDirectory, port);

        // 6. Build the launch command to run from the isolated directory
        String javaExecutable = this.resolveJavaExecutable();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        this.addMemoryArguments(command);
        command.add("-Dminiverse.session.config=" + workingDirectory.resolve("miniverse-session.json").toAbsolutePath());
        command.add("-Dminiverse.session.game=" + session.getGameType().getCommandName());
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && SessionPermissions.isDevBypassEnabled()) {
            command.add("-Dminiverse.devSession=true");
            command.add("-Dminiverse.session.devBypass=true");
        }
        command.add("-jar");
        command.add("fabric-server-launch.jar");
        command.add("nogui");

        Miniverse.LOGGER.info("Backend relaunch command: {}", String.join(" ", command));

        long startTime = System.currentTimeMillis();

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectOutput(workingDirectory.resolve("stdout.log").toFile());
        builder.redirectError(workingDirectory.resolve("stderr.log").toFile());

        Process process = builder.start();

        // 7. Wait for the server to boot and mark it as running
        boolean booted = waitForServerBoot(workingDirectory.resolve("stdout.log"), process);
        if (!booted) {
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            throw new IOException("Server failed to boot. Exit code: " + exitCode);
        }

        long bootTime = System.currentTimeMillis() - startTime;
        int publicPort = SessionLauncherConfig.getInstance().publicPortForLocalPort(port);
        String address = SessionServerConfig.getInstance().advertisedHost() + ":" + publicPort;
        group.markRunning(process, address);

        if (publicPort != port) {
            Miniverse.LOGGER.info("Relaunched {} session {} for {} at {} forwarding to local port {} (Boot time: {}ms)", session.getGameType().getDisplayName(), session.getSessionId(), group.getDisplayName(), address, port, bootTime);
        } else {
            Miniverse.LOGGER.info("Relaunched {} session {} for {} at {} (Boot time: {}ms)", session.getGameType().getDisplayName(), session.getSessionId(), group.getDisplayName(), address, bootTime);
        }
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

    public InspectionLaunchResult launchInspection(String sessionId, Path sourceSessionRoot, ServerPlayerEntity viewer) throws IOException {
        Path sourceWorld = this.findInspectableWorld(sourceSessionRoot)
            .orElseThrow(() -> new IOException("No inspectable world found for retained session " + sessionId));
        Path serverRoot = detectServerRoot();
        validateSharedRuntime(serverRoot);

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            this.buildLatestModJar();
            this.syncLatestModJar(serverRoot);
        }

        Path workingDirectory = this.prepareInspectionDirectory(sessionId);
        int port = this.reservePort();
        this.copyDirectory(sourceWorld, workingDirectory.resolve("world"));
        this.createRuntimeSymlinks(workingDirectory, serverRoot);
        this.writeEula(workingDirectory);
        this.writeInspectionServerProperties(workingDirectory, sessionId, port);
        this.writeInspectionConfig(workingDirectory);
        this.writeInspectionOps(workingDirectory, viewer);

        String javaExecutable = this.resolveJavaExecutable();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        this.addMemoryArguments(command);
        command.add("-Dminiverse.inspection=true");
        command.add("-jar");
        command.add("fabric-server-launch.jar");
        command.add("nogui");

        Miniverse.LOGGER.info("Inspection launch command: {}", String.join(" ", command));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectOutput(workingDirectory.resolve("stdout.log").toFile());
        builder.redirectError(workingDirectory.resolve("stderr.log").toFile());

        Process process = builder.start();
        if (!waitForServerBoot(workingDirectory.resolve("stdout.log"), process)) {
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            throw new IOException("Inspection server failed to boot. Exit code: " + exitCode);
        }

        Miniverse.LOGGER.info("Launched inspection copy for retained session {} on local port {}", sessionId, port);
        return new InspectionLaunchResult(process, port, workingDirectory);
    }

    private void addMemoryArguments(List<String> command) {
        SessionMemoryConfig memoryConfig = SessionMemoryConfig.getInstance();
        if (!memoryConfig.isEnabled()) {
            Miniverse.LOGGER.info("Session memory limits are disabled; backend JVM will use default heap sizing.");
            return;
        }

        command.add(memoryConfig.getInitialHeapArg());
        command.add(memoryConfig.getMaxHeapArg());
        Miniverse.LOGGER.info(
            "Applying session memory limits: initial heap {}, max heap {}.",
            memoryConfig.getInitialHeapArg(),
            memoryConfig.getMaxHeapArg()
        );
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
    private Path prepareWorkingDirectory(GameSession session, SessionGroup group, String directorySuffix) throws IOException {
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

    private Path prepareInspectionDirectory(String sessionId) throws IOException {
        Path inspectionsRoot = MiniversePaths.runRoot().resolve("session-inspections");
        Files.createDirectories(inspectionsRoot);
        Path directory = inspectionsRoot.resolve(this.sanitize(sessionId) + "_" + System.currentTimeMillis());
        Files.createDirectories(directory);
        Files.createDirectories(directory.resolve("logs"));
        return directory;
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
        this.linkOptionalDirectory(workingDirectory.resolve("versions"), serverRoot.resolve("versions"));
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
        if (!Files.isDirectory(target)) {
            throw new IOException("Runtime directory is missing: " + target);
        }

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

    private void linkOptionalDirectory(Path link, Path target) throws IOException {
        if (Files.isDirectory(target)) {
            this.linkDirectory(link, target);
        }
    }

    private void buildLatestModJar() throws IOException {
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

    private void syncLatestModJar(Path serverRoot) throws IOException {
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
                SessionConfigJson.runtimeSession(session, group, groupsForConfig(session, group), settingsProperties, properties.getProperty("return.host"), this.parsePort(properties.getProperty("return.port"), 25565), MiniversePaths.sessionsRoot())
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

    private void writeInspectionServerProperties(Path workingDirectory, String sessionId, int port) throws IOException {
        Properties properties = new Properties();
        SessionServerConfig serverConfig = SessionServerConfig.getInstance();
        properties.setProperty("accepts-transfers", Boolean.toString(serverConfig.acceptsTransfers()));
        properties.setProperty("allow-flight", "true");
        properties.setProperty("allow-nether", "true");
        properties.setProperty("broadcast-console-to-ops", "true");
        properties.setProperty("difficulty", "peaceful");
        properties.setProperty("enable-command-block", "false");
        properties.setProperty("enable-rcon", "false");
        properties.setProperty("enable-status", "true");
        properties.setProperty("enforce-secure-profile", "false");
        properties.setProperty("force-gamemode", "true");
        properties.setProperty("gamemode", "spectator");
        properties.setProperty("level-name", "world");
        properties.setProperty("max-players", "4");
        properties.setProperty("motd", "Miniverse inspection copy for " + sessionId);
        properties.setProperty("network-compression-threshold", "256");
        properties.setProperty("online-mode", Boolean.toString(serverConfig.onlineMode()));
        properties.setProperty("op-permission-level", "4");
        properties.setProperty("player-idle-timeout", "0");
        properties.setProperty("prevent-proxy-connections", "false");
        properties.setProperty("pvp", "false");
        properties.setProperty("query.port", Integer.toString(port));
        properties.setProperty("rate-limit", "0");
        properties.setProperty("server-ip", "");
        properties.setProperty("server-port", Integer.toString(port));
        properties.setProperty("simulation-distance", Integer.toString(serverConfig.simulationDistance()));
        properties.setProperty("spawn-monsters", "false");
        properties.setProperty("spawn-protection", "0");
        properties.setProperty("sync-chunk-writes", "true");
        properties.setProperty("use-native-transport", "true");
        properties.setProperty("view-distance", Integer.toString(serverConfig.viewDistance()));
        properties.setProperty("white-list", "false");

        StringBuilder content = new StringBuilder();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            content.append(key).append('=').append(properties.getProperty(key)).append('\n');
        }
        Files.writeString(workingDirectory.resolve("server.properties"), content.toString(), StandardCharsets.UTF_8);
    }

    private void writeInspectionConfig(Path workingDirectory) throws IOException {
        // Create minimal JSON config for inspection server to know how to return
        JsonObject config = new JsonObject();
        config.addProperty("return.host", this.resolveReturnHost());
        config.addProperty("return.port", this.resolveReturnPort());
        config.addProperty("registry.sessionsRoot", MiniversePaths.sessionsRoot().toString());

        // Write as JSON
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(
            workingDirectory.resolve("miniverse-session.json"),
            gson.toJson(config),
            StandardCharsets.UTF_8
        );
    }

    private void writeInspectionOps(Path workingDirectory, ServerPlayerEntity viewer) throws IOException {
        if (viewer == null) {
            return;
        }
        new SessionOperatorSnapshot(List.of(new SessionOperatorSnapshot.Entry(
            viewer.getUuid(),
            viewer.getName().getString(),
            4,
            true
        ))).writeOpsJson(workingDirectory);
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
        SessionLauncherConfig config = SessionLauncherConfig.getInstance();
        if (config.hasSessionPortRange()) {
            for (int port = config.sessionPortStart(); port <= config.sessionPortEnd(); port++) {
                if (this.isPortAvailable(port)) {
                    return port;
                }
            }
            throw new IOException("No free backend session ports in configured range "
                + config.sessionPortStart() + "-" + config.sessionPortEnd()
                + ". Increase config/miniverse/session-launcher.json sessionPortStart/sessionPortEnd or stop an active session.");
        }

        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String resolveReturnHost() throws IOException {
        Properties properties = this.readMainServerProperties();
        String serverIp = properties.getProperty("server-ip", "").trim();
        return serverIp.isBlank() ? SessionServerConfig.getInstance().advertisedHost() : serverIp;
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

    private Optional<Path> findInspectableWorld(Path sourceSessionRoot) throws IOException {
        if (sourceSessionRoot == null || !Files.isDirectory(sourceSessionRoot)) {
            return Optional.empty();
        }
        try (var stream = Files.list(sourceSessionRoot)) {
            return stream
                .filter(path -> Files.isDirectory(path.resolve("world")))
                .map(path -> path.resolve("world"))
                .findFirst();
        }
    }

    private Optional<Path> findRetainedWorld(Path sourceSessionRoot, String groupLabel) throws IOException {
        if (sourceSessionRoot == null || !Files.isDirectory(sourceSessionRoot)) {
            return Optional.empty();
        }
        String labelPrefix = groupLabel == null ? "" : this.sanitize(groupLabel).toLowerCase();
        Path best = null;
        long bestTime = Long.MIN_VALUE;
        try (var stream = Files.list(sourceSessionRoot)) {
            for (Path path : stream.toList()) {
                if (!Files.isDirectory(path.resolve("world"))) {
                    continue;
                }
                String folderName = path.getFileName().toString().toLowerCase();
                if (!labelPrefix.isBlank() && !folderName.startsWith(labelPrefix)) {
                    continue;
                }
                long modified = Files.getLastModifiedTime(path).toMillis();
                if (modified >= bestTime) {
                    bestTime = modified;
                    best = path.resolve("world");
                }
            }
        }
        if (best != null) {
            return Optional.of(best);
        }
        return this.findInspectableWorld(sourceSessionRoot);
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Source directory does not exist: " + source);
        }
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            for (Path sourcePath : stream.toList()) {
                Path relative = source.relativize(sourcePath);
                Path targetPath = target.resolve(relative);
                BasicFileAttributes attrs = Files.readAttributes(sourcePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else if (attrs.isRegularFile()) {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                }
            }
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
