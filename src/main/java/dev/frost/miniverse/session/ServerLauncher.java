package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniverseFileUtils;
import dev.frost.miniverse.common.MiniversePaths;
import dev.frost.miniverse.map.MapStore;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;


import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
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
import java.util.function.Consumer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ServerLauncher {
    private final WorkingDirectorySetup setup = new WorkingDirectorySetup();
    private final SessionConfigBuilder configBuilder = new SessionConfigBuilder(this);
    private static final int DEFAULT_SESSION_MAX_PLAYERS = 20;
    private static final int MIN_LATE_JOIN_HEADROOM = 8;

    public record LaunchResult(SessionGroup group, Process process, int port, Path workingDirectory) {
    }

    public record InspectionLaunchResult(Process process, int port, Path workingDirectory) {
    }

    public record MapEditorLaunchResult(Process process, int port, Path workingDirectory, String mapId) {
    }

    public record LaunchProgress(String stage, String detail, int progress) {
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
        return this.launch(session, group, operatorSnapshot, directorySuffix, syncDevelopmentRuntime, progress -> {
        });
    }

    public LaunchResult launch(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, String directorySuffix, boolean syncDevelopmentRuntime, Consumer<LaunchProgress> progressConsumer) throws IOException {
        Consumer<LaunchProgress> progress = progressConsumer == null ? ignored -> {
        } : progressConsumer;
        progress.accept(new LaunchProgress("Preparing files", group.getDisplayName(), 20));
        // 1. Detect and validate the shared server runtime
        Path serverRoot = setup.detectServerRoot();
        setup.validateSharedRuntime(serverRoot);

        if (syncDevelopmentRuntime && FabricLoader.getInstance().isDevelopmentEnvironment()) {
            setup.buildLatestModJar();
            setup.syncLatestModJar(serverRoot);
        }

        // 2. Prepare an isolated working directory for the session
        Path workingDirectory = setup.prepareWorkingDirectory(session, group, directorySuffix);
        int port = this.reservePort();
        progress.accept(new LaunchProgress("Preparing files", "Reserved backend port " + port, 35));

        // 3. Create symbolic links/junctions to the shared runtime files
        setup.createRuntimeSymlinks(workingDirectory, serverRoot);
        this.copySelectedMapTemplate(session, workingDirectory);

        // 4. Write session-specific configuration files into the working directory
        this.writeEula(workingDirectory);
        SessionRegistry.clearStopRequested(session.getSessionId());
        SessionRegistry.clearReturnComplete(session.getSessionId());
        SessionRegistry.clearSeedChangeRequested(session.getSessionId());
        configBuilder.writeSessionConfig(workingDirectory, session, group, BackendLaunchMode.NEW_SESSION);
        SessionOperatorSnapshot snapshot = operatorSnapshot == null ? SessionOperatorSnapshot.empty() : operatorSnapshot;
        snapshot.writeOpsJson(workingDirectory);
        configBuilder.writeServerProperties(workingDirectory, session, group, port);

        group.markLaunching(workingDirectory, port);
        progress.accept(new LaunchProgress("Starting server", "Launching backend process", 55));

        // 5. Build the launch command to run from the isolated directory
        String javaExecutable = this.resolveJavaExecutable();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        this.addMemoryArguments(command);
        command.add("-Dminiverse.runRoot=" + MiniversePaths.runRoot().toAbsolutePath());
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
        progress.accept(new LaunchProgress("Waiting for boot", "Backend is starting", 70));

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
        progress.accept(new LaunchProgress("Ready", "Backend is ready at " + address, 95));

        if (publicPort != port) {
            Miniverse.LOGGER.info("Launched {} session {} for {} at {} forwarding to local port {} (Boot time: {}ms)", session.getGameType().getDisplayName(), session.getSessionId(), group.getDisplayName(), address, port, bootTime);
        } else {
            Miniverse.LOGGER.info("Launched {} session {} for {} at {} (Boot time: {}ms)", session.getGameType().getDisplayName(), session.getSessionId(), group.getDisplayName(), address, bootTime);
        }
        return new LaunchResult(group, process, port, workingDirectory);
    }

    public LaunchResult launchFromRetained(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, Path retainedSessionRoot, boolean syncDevelopmentRuntime) throws IOException {
        return this.launchFromRetained(session, group, operatorSnapshot, retainedSessionRoot, syncDevelopmentRuntime, progress -> {
        });
    }

    public LaunchResult launchFromRetained(GameSession session, SessionGroup group, SessionOperatorSnapshot operatorSnapshot, Path retainedSessionRoot, boolean syncDevelopmentRuntime, Consumer<LaunchProgress> progressConsumer) throws IOException {
        Consumer<LaunchProgress> progress = progressConsumer == null ? ignored -> {
        } : progressConsumer;
        progress.accept(new LaunchProgress("Preparing files", "Resuming retained files for " + group.getDisplayName(), 20));
        // 1. Detect and validate the shared server runtime
        Path serverRoot = setup.detectServerRoot();
        setup.validateSharedRuntime(serverRoot);

        if (syncDevelopmentRuntime && FabricLoader.getInstance().isDevelopmentEnvironment()) {
            setup.buildLatestModJar();
            setup.syncLatestModJar(serverRoot);
        }

        // 2. Reuse the retained backend folder so world, playerdata, and runtime saves stay authoritative.
        Path workingDirectory = this.findRetainedGroupDirectory(retainedSessionRoot, group.getGroupLabel())
            .orElseThrow(() -> new IOException("No retained backend folder found for session " + session.getSessionId()));
        Files.createDirectories(workingDirectory.resolve("logs"));
        int port = this.reservePort();

        // 3. Refresh runtime links/junctions without touching session-owned data.
        setup.replaceRuntimeSymlinks(workingDirectory, serverRoot);
        progress.accept(new LaunchProgress("Preparing files", "Retained backend folder ready", 40));

        // 4. Write session-specific configuration files into the working directory
        this.writeEula(workingDirectory);
        SessionRegistry.clearStopRequested(session.getSessionId());
        SessionRegistry.clearReturnComplete(session.getSessionId());
        SessionRegistry.clearSeedChangeRequested(session.getSessionId());
        configBuilder.writeSessionConfig(workingDirectory, session, group, BackendLaunchMode.RESTORE_SESSION);
        SessionOperatorSnapshot snapshot = operatorSnapshot == null ? SessionOperatorSnapshot.empty() : operatorSnapshot;
        snapshot.writeOpsJson(workingDirectory);
        configBuilder.writeServerProperties(workingDirectory, session, group, port);

        group.markLaunching(workingDirectory, port);
        progress.accept(new LaunchProgress("Starting server", "Launching backend process", 55));

        // 5. Build the launch command to run from the retained directory
        String javaExecutable = this.resolveJavaExecutable();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        this.addMemoryArguments(command);
        command.add("-Dminiverse.runRoot=" + MiniversePaths.runRoot().toAbsolutePath());
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
        progress.accept(new LaunchProgress("Waiting for boot", "Backend is starting", 70));

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
        progress.accept(new LaunchProgress("Ready", "Backend is ready at " + address, 95));

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
            this.requestGracefulStop(process);
            try {
                if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
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

    private void requestGracefulStop(Process process) {
        try {
            Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
            writer.write("stop\n");
            writer.flush();
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to send graceful stop to backend; terminating process instead: {}", e.getMessage());
            process.destroy();
        }
    }

    public InspectionLaunchResult launchInspection(String sessionId, Path sourceSessionRoot, ServerPlayerEntity viewer) throws IOException {
        Path sourceWorld = this.findInspectableWorld(sourceSessionRoot)
            .orElseThrow(() -> new IOException("No inspectable world found for retained session " + sessionId));
        Path serverRoot = setup.detectServerRoot();
        setup.validateSharedRuntime(serverRoot);

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            setup.buildLatestModJar();
            setup.syncLatestModJar(serverRoot);
        }

        Path workingDirectory = setup.prepareInspectionDirectory(sessionId);
        int port = this.reservePort();
        this.copyDirectory(sourceWorld, workingDirectory.resolve("world"));
        setup.createRuntimeSymlinks(workingDirectory, serverRoot);
        this.writeEula(workingDirectory);
        configBuilder.writeInspectionServerProperties(workingDirectory, sessionId, port);
        configBuilder.writeInspectionConfig(workingDirectory);
        this.writeInspectionOps(workingDirectory, viewer);

        String javaExecutable = this.resolveJavaExecutable();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        this.addMemoryArguments(command);
        command.add("-Dminiverse.runRoot=" + MiniversePaths.runRoot().toAbsolutePath());
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

    public MapEditorLaunchResult launchMapEditor(String mapName, ServerPlayerEntity editor) throws IOException {
        dev.frost.miniverse.map.MapDescriptor map = MapStore.createEmptyMap(mapName);
        return this.launchMapEditor(map, editor, false);
    }

    public MapEditorLaunchResult launchMapEditorForExistingMap(String mapId, ServerPlayerEntity editor) throws IOException {
        dev.frost.miniverse.map.MapDescriptor map = MapStore.find(mapId)
            .orElseThrow(() -> new IOException("Unknown map '" + mapId + "'."));
        return this.launchMapEditor(map, editor, true);
    }

    private MapEditorLaunchResult launchMapEditor(dev.frost.miniverse.map.MapDescriptor map, ServerPlayerEntity editor, boolean copyTemplateWorld) throws IOException {
        Path serverRoot = setup.detectServerRoot();
        setup.validateSharedRuntime(serverRoot);

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            setup.buildLatestModJar();
            setup.syncLatestModJar(serverRoot);
        }

        Path workingDirectory = setup.prepareMapEditorDirectory(map.metadata().id());
        int port = this.reservePort();
        setup.createRuntimeSymlinks(workingDirectory, serverRoot);
        if (copyTemplateWorld && Files.isDirectory(map.worldFolder())) {
            MapStore.copyTemplateWorldToRuntime(map.metadata().id(), workingDirectory.resolve("world"));
        }
        this.writeEula(workingDirectory);
        configBuilder.writeMapEditorServerProperties(workingDirectory, map.metadata().id(), port);
        configBuilder.writeMapEditorConfig(workingDirectory, map);
        this.writeInspectionOps(workingDirectory, editor);

        String javaExecutable = this.resolveJavaExecutable();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        this.addMemoryArguments(command);
        command.add("-Dminiverse.runRoot=" + MiniversePaths.runRoot().toAbsolutePath());
        command.add("-Dminiverse.mapEditor=true");
        command.add("-Dminiverse.session.config=" + workingDirectory.resolve("miniverse-session.json").toAbsolutePath());
        command.add("-jar");
        command.add("fabric-server-launch.jar");
        command.add("nogui");

        Miniverse.LOGGER.info("Map editor launch command: {}", String.join(" ", command));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectOutput(workingDirectory.resolve("stdout.log").toFile());
        builder.redirectError(workingDirectory.resolve("stderr.log").toFile());

        Process process = builder.start();
        if (!waitForServerBoot(workingDirectory.resolve("stdout.log"), process)) {
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            throw new IOException("Map editor server failed to boot. Exit code: " + exitCode);
        }

        Miniverse.LOGGER.info("Launched map editor for {} on local port {}", map.metadata().id(), port);
        return new MapEditorLaunchResult(process, port, workingDirectory, map.metadata().id());
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





    private void writeEula(Path workingDirectory) throws IOException {
        Path eula = workingDirectory.resolve("eula.txt");
        Files.writeString(eula, "eula=true\n", java.nio.charset.StandardCharsets.UTF_8);
    }

    private void copySelectedMapTemplate(GameSession session, Path workingDirectory) throws IOException {
        NbtCompound settings = session.getSettings();
        if (!settings.contains("mapId", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            return;
        }
        String mapId = settings.getString("mapId").trim();
        if (mapId.isBlank()) {
            return;
        }
        MapStore.copyTemplateWorldToRuntime(mapId, workingDirectory.resolve("world"));
        Miniverse.LOGGER.info("Copied map template '{}' into runtime world for session {}", mapId, session.getSessionId());
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
        Path javaBin = Paths.get(javaHome, "bin", setup.isWindows() ? "java.exe" : "java");
        return javaBin.toString();
    }

    List<SessionGroup> groupsForConfig(GameSession session, SessionGroup group) {
        if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD) {
            return List.copyOf(session.snapshotGroups());
        }
        return List.of(group);
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

    String resolveReturnHost() throws IOException {
        Properties properties = this.readMainServerProperties();
        String serverIp = properties.getProperty("server-ip", "").trim();
        return serverIp.isBlank() ? SessionServerConfig.getInstance().advertisedHost() : serverIp;
    }

    int resolveReturnPort() throws IOException {
        Properties properties = this.readMainServerProperties();
        String value = properties.getProperty("server-port", "25565").trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 25565;
        }
    }

    int resolveSessionMaxPlayers(int assignedPlayers) throws IOException {
        Properties properties = this.readMainServerProperties();
        int lobbyMaxPlayers = this.parsePort(properties.getProperty("max-players", ""), DEFAULT_SESSION_MAX_PLAYERS);
        int withHeadroom = assignedPlayers + MIN_LATE_JOIN_HEADROOM;
        return Math.max(1, Math.max(lobbyMaxPlayers, withHeadroom));
    }

    Properties readMainServerProperties() throws IOException {
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

    int parsePort(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Optional<Path> findInspectableWorld(Path sourceSessionRoot) throws IOException {
        return this.findInspectableGroupDirectory(sourceSessionRoot).map(path -> path.resolve("world"));
    }

    private Optional<Path> findInspectableGroupDirectory(Path sourceSessionRoot) throws IOException {
        if (sourceSessionRoot == null || !Files.isDirectory(sourceSessionRoot)) {
            return Optional.empty();
        }
        try (var stream = Files.list(sourceSessionRoot)) {
            return stream
                .filter(path -> Files.isDirectory(path.resolve("world")))
                .findFirst();
        }
    }

    private Optional<Path> findRetainedGroupDirectory(Path sourceSessionRoot, String groupLabel) throws IOException {
        if (sourceSessionRoot == null || !Files.isDirectory(sourceSessionRoot)) {
            return Optional.empty();
        }
        String labelPrefix = groupLabel == null ? "" : setup.sanitize(groupLabel).toLowerCase();
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
                long modified = MiniverseFileUtils.lastModifiedMillis(path);
                if (modified >= bestTime) {
                    bestTime = modified;
                    best = path;
                }
            }
        }
        if (best != null) {
            return Optional.of(best);
        }
        return this.findInspectableGroupDirectory(sourceSessionRoot);
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
}
