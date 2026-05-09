package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntSettings;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapSettings;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntSettings;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintSettings;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

public final class ServerLauncher {
    public record LaunchResult(PlayerAssignment assignment, Process process, int port, Path workingDirectory) {
    }

    public LaunchResult launch(GameSession session, PlayerAssignment assignment) throws IOException {
        int port = this.reservePort();
        Path workingDirectory = this.prepareWorkingDirectory(session, assignment);
        this.writeEula(workingDirectory);
        this.writeSessionConfig(workingDirectory, session, assignment);
        this.writeServerProperties(workingDirectory, session, assignment, port);

        assignment.markLaunching(workingDirectory, port);

        List<String> command = this.buildCommand(workingDirectory, session, port);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(workingDirectory.resolve("session.log").toFile());

        Process process = builder.start();
        this.waitForPortOpen(process, port);
        String address = "127.0.0.1:" + port;
        assignment.markRunning(process, address);

        Miniverse.LOGGER.info("Launched {} session {} for {} at {}", session.getGameType().getDisplayName(), session.getSessionId(), assignment.getDisplayName(), address);
        return new LaunchResult(assignment, process, port, workingDirectory);
    }

    public void stop(PlayerAssignment assignment) {
        Process process = assignment.getProcess();
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        assignment.markStopped();
    }

    private List<String> buildCommand(Path workingDirectory, GameSession session, int port) throws IOException {
        String javaExecutable = this.resolveJavaExecutable();
        Path argumentsFile = workingDirectory.resolve("launch.args");
        this.writeArgumentsFile(argumentsFile, workingDirectory, session, port);

        return List.of(javaExecutable, "@" + argumentsFile.toAbsolutePath());
    }

    private void writeArgumentsFile(Path argumentsFile, Path workingDirectory, GameSession session, int port) throws IOException {
        String classPath = System.getProperty("java.class.path");
        String launchConfig = this.locateLaunchConfig();

        StringBuilder content = new StringBuilder();
        for (String inputArgument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            this.appendArgument(content, inputArgument);
        }

        // Apply custom memory allocation if configured
        SessionMemoryConfig memoryConfig = SessionMemoryConfig.getInstance();
        if (memoryConfig.isEnabled()) {
            this.appendArgument(content, memoryConfig.getInitialHeapArg());
            this.appendArgument(content, memoryConfig.getMaxHeapArg());
        }

        this.appendArgument(content, "-Dfabric.dli.config=" + launchConfig);
        this.appendArgument(content, "-Dfabric.dli.env=server");
        this.appendArgument(content, "-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer");
        this.appendArgument(content, "-Dminiverse.session.port=" + port);
        this.appendArgument(content, "-Dminiverse.session.dir=" + workingDirectory.toAbsolutePath());
        this.appendArgument(content, "-Dminiverse.session.game=" + session.getGameType().getCommandName());
        this.appendArgument(content, "-Dminiverse.session.config=" + workingDirectory.resolve("miniverse-session.properties").toAbsolutePath());
        if (session.getGameType() == SessionGameType.MANHUNT) {
            NbtCompound settingsNbt = session.getSettings();
            ManhuntSettings settings = ManhuntSettings.fromNbt(settingsNbt);
            this.appendArgument(content, "-Dminiverse.manhunt.hunterReleaseDelaySeconds=" + settings.hunterReleaseDelaySeconds());
            this.appendArgument(content, "-Dminiverse.manhunt.respawnDelaySeconds=" + settings.speedrunnerRespawnDelaySeconds());
            this.appendArgument(content, "-Dminiverse.manhunt.huntersCompass=" + settings.huntersCompassEnabled());
            this.appendArgument(content, "-Dminiverse.manhunt.netherTracking=" + settings.netherTrackingEnabled());
            this.appendArgument(content, "-Dminiverse.manhunt.compassCooldownSeconds=" + settings.compassCooldownSeconds());
            this.appendArgument(content, "-Dminiverse.manhunt.runnerGlowPulseMinutes=" + settings.runnerGlowPulseMinutes());
            this.appendArgument(content, "-Dminiverse.manhunt.runnerLives=" + settings.runnerLives());
            this.appendArgument(content, "-Dminiverse.manhunt.hunterLives=" + settings.hunterLives());
            this.appendArgument(content, "-Dminiverse.manhunt.hunterRespawnDelaySeconds=" + settings.hunterRespawnDelaySeconds());
        }
        if (session.getGameType() == SessionGameType.BOUNTY_HUNT) {
            NbtCompound settingsNbt = session.getSettings();
            BountyHuntSettings settings = BountyHuntSettings.fromNbt(settingsNbt);
            this.appendArgument(content, "-Dminiverse.bountyhunt.gracePeriodSeconds=" + settings.gracePeriodSeconds());
            this.appendArgument(content, "-Dminiverse.bountyhunt.respawnInvincibilitySeconds=" + settings.respawnInvincibilitySeconds());
            this.appendArgument(content, "-Dminiverse.bountyhunt.scoreToWin=" + settings.scoreToWin());
            this.appendArgument(content, "-Dminiverse.bountyhunt.targetSwapIntervalSeconds=" + settings.targetSwapIntervalSeconds());
            this.appendArgument(content, "-Dminiverse.bountyhunt.trackerEnabled=" + settings.trackerEnabled());
            this.appendArgument(content, "-Dminiverse.bountyhunt.netherTracking=" + settings.netherTrackingEnabled());
            this.appendArgument(content, "-Dminiverse.bountyhunt.compassCooldownSeconds=" + settings.compassCooldownSeconds());
            this.appendArgument(content, "-Dminiverse.bountyhunt.trackerItemId=" + settings.trackerItemId());
        }
        this.appendArgument(content, "-cp");
        this.appendArgument(content, classPath);
        this.appendArgument(content, "net.fabricmc.devlaunchinjector.Main");
        this.appendArgument(content, "nogui");

        Files.writeString(argumentsFile, content.toString(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private void waitForPortOpen(Process process, int port) throws IOException {
        long deadline = System.currentTimeMillis() + 90_000L;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IOException("Launched server exited before opening port " + port + ".");
            }

            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 750);
                return;
            } catch (IOException ignored) {
                LockSupport.parkNanos(500_000_000L);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Interrupted while waiting for server port " + port + ".");
                }
            }
        }

        throw new IOException("Timed out waiting for launched server to open port " + port + ".");
    }

    private Path prepareWorkingDirectory(GameSession session, PlayerAssignment assignment) throws IOException {
        Path sessionsRoot = this.locateProjectRoot().resolve("run").resolve("sessions");
        String assignmentFolder = this.sanitize(assignment.getAssignmentLabel());
        Path sessionDirectory = sessionsRoot.resolve(session.getSessionId()).resolve(assignmentFolder);
        if (Files.exists(sessionDirectory)) {
            try {
                deleteRecursively(sessionDirectory);
            } catch (IOException e) {
                String fallbackName = assignmentFolder + "_" + System.currentTimeMillis();
                Path fallback = sessionsRoot.resolve(session.getSessionId()).resolve(fallbackName);
                Miniverse.LOGGER.warn("Failed to clear session dir {}; using {} instead", sessionDirectory, fallback, e);
                sessionDirectory = fallback;
            }
        }
        Files.createDirectories(sessionDirectory);
        Files.createDirectories(sessionDirectory.resolve("logs"));
        return sessionDirectory;
    }

    private void writeEula(Path workingDirectory) throws IOException {
        Path eula = workingDirectory.resolve("eula.txt");
        Files.writeString(eula, "eula=true\n", java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeSessionConfig(Path workingDirectory, GameSession session, PlayerAssignment assignment) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("game", session.getGameType().getCommandName());
        properties.setProperty("sessionId", session.getSessionId());
        properties.setProperty("assignmentLabel", assignment.getAssignmentLabel());
        properties.setProperty("return.host", this.resolveReturnHost());
        properties.setProperty("return.port", Integer.toString(this.resolveReturnPort()));

        NbtCompound settings = session.getSettings();
        if (session.getGameType() == SessionGameType.MANHUNT) {
            ManhuntSettings manhunt = ManhuntSettings.fromNbt(settings);
            properties.setProperty("manhunt.hunterReleaseDelaySeconds", Integer.toString(manhunt.hunterReleaseDelaySeconds()));
            properties.setProperty("manhunt.speedrunnerRespawnDelaySeconds", Integer.toString(manhunt.speedrunnerRespawnDelaySeconds()));
            properties.setProperty("manhunt.huntersCompass", Boolean.toString(manhunt.huntersCompassEnabled()));
            properties.setProperty("manhunt.netherTracking", Boolean.toString(manhunt.netherTrackingEnabled()));
            properties.setProperty("manhunt.compassCooldownSeconds", Integer.toString(manhunt.compassCooldownSeconds()));
            properties.setProperty("manhunt.runnerGlowPulseMinutes", Integer.toString(manhunt.runnerGlowPulseMinutes()));
            properties.setProperty("manhunt.runnerLives", Integer.toString(manhunt.runnerLives()));
            properties.setProperty("manhunt.hunterLives", Integer.toString(manhunt.hunterLives()));
            properties.setProperty("manhunt.hunterRespawnDelaySeconds", Integer.toString(manhunt.hunterRespawnDelaySeconds()));

            NbtList roles = settings.getList("roles").orElseGet(NbtList::new);
            for (int i = 0; i < roles.size(); i++) {
                NbtCompound role = roles.getCompoundOrEmpty(i);
                String uuid = role.getString("uuid", "");
                String roleName = role.getString("role", "");
                if (!uuid.isBlank() && !roleName.isBlank()) {
                    properties.setProperty("manhunt.role." + uuid, roleName);
                }
            }
        }
        if (session.getGameType() == SessionGameType.BOUNTY_HUNT) {
            BountyHuntSettings bountyHunt = BountyHuntSettings.fromNbt(settings);
            properties.setProperty("bountyhunt.gracePeriodSeconds", Integer.toString(bountyHunt.gracePeriodSeconds()));
            properties.setProperty("bountyhunt.respawnInvincibilitySeconds", Integer.toString(bountyHunt.respawnInvincibilitySeconds()));
            properties.setProperty("bountyhunt.scoreToWin", Integer.toString(bountyHunt.scoreToWin()));
            properties.setProperty("bountyhunt.targetSwapIntervalSeconds", Integer.toString(bountyHunt.targetSwapIntervalSeconds()));
            properties.setProperty("bountyhunt.trackerEnabled", Boolean.toString(bountyHunt.trackerEnabled()));
            properties.setProperty("bountyhunt.netherTracking", Boolean.toString(bountyHunt.netherTrackingEnabled()));
            properties.setProperty("bountyhunt.compassCooldownSeconds", Integer.toString(bountyHunt.compassCooldownSeconds()));
            properties.setProperty("bountyhunt.trackerItemId", bountyHunt.trackerItemId());
        }
        if (session.getGameType() == SessionGameType.RESOURCE_SPRINT) {
            ResourceSprintSettings.fromNbt(settings).writeTo(properties);
        }
        if (session.getGameType() == SessionGameType.DEATH_SWAP) {
            DeathSwapSettings.fromNbt(settings).writeTo(properties);
        }

        for (PlayerAssignment sessionAssignment : assignmentsForConfig(session, assignment)) {
            String teamLabel = sessionAssignment.getAssignmentLabel();
            for (UUID playerUuid : sessionAssignment.getPlayerUuids()) {
                properties.setProperty("player." + playerUuid, "true");
                properties.setProperty("player." + playerUuid + ".team", teamLabel);
            }
        }

        StringBuilder content = new StringBuilder();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            content.append(key).append('=').append(properties.getProperty(key)).append('\n');
        }
        Files.writeString(workingDirectory.resolve("miniverse-session.properties"), content.toString(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeServerProperties(Path workingDirectory, GameSession session, PlayerAssignment assignment, int port) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("accepts-transfers", "true");
        properties.setProperty("allow-flight", "true");
        properties.setProperty("allow-nether", "true");
        properties.setProperty("broadcast-console-to-ops", "true");
        properties.setProperty("broadcast-rcon-to-ops", "true");
        properties.setProperty("difficulty", "normal");
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
        int maxPlayers = assignmentsForConfig(session, assignment).stream()
            .mapToInt(PlayerAssignment::getPlayerCount)
            .sum();
        properties.setProperty("max-players", Integer.toString(Math.max(1, maxPlayers)));
        properties.setProperty("motd", session.getGameType().getDisplayName() + " " + session.getSessionId() + " / " + assignment.getAssignmentLabel());
        properties.setProperty("network-compression-threshold", "256");
        properties.setProperty("online-mode", "false");
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
        properties.setProperty("simulation-distance", "6");
        properties.setProperty("spawn-monsters", "true");
        properties.setProperty("spawn-protection", "0");
        properties.setProperty("sync-chunk-writes", "true");
        properties.setProperty("text-filtering-config", "");
        properties.setProperty("use-native-transport", "true");
        properties.setProperty("view-distance", "8");
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

    private List<PlayerAssignment> assignmentsForConfig(GameSession session, PlayerAssignment assignment) {
        if (session.getGameType().getTopology() == SessionTopology.SHARED_WORLD) {
            return List.copyOf(session.snapshotAssignments());
        }
        return List.of(assignment);
    }

    private String locateLaunchConfig() throws IOException {
        Path projectRoot = this.locateProjectRoot();
        Path launchConfig = projectRoot.resolve(".gradle").resolve("loom-cache").resolve("launch.cfg");
        if (!Files.exists(launchConfig)) {
            throw new IOException("Could not locate Fabric launch config at " + launchConfig);
        }
        return launchConfig.toAbsolutePath().toString();
    }

    private Path locateProjectRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("gradle.properties"))) {
                return current;
            }
            current = current.getParent();
        }

        return Paths.get("").toAbsolutePath();
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

    private void appendArgument(StringBuilder content, String argument) {
        if (argument == null || argument.isBlank()) {
            return;
        }

        if (argument.matches(".*\\s.*") || argument.contains("\"") || argument.contains("'")) {
            content.append('"').append(argument.replace("\"", "\\\"")).append('"').append('\n');
        } else {
            content.append(argument).append('\n');
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
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
        Path serverProperties = this.locateProjectRoot().resolve("run").resolve("server.properties");
        Properties properties = new Properties();
        if (!Files.exists(serverProperties)) {
            return properties;
        }

        try (java.io.Reader reader = Files.newBufferedReader(serverProperties)) {
            properties.load(reader);
        }
        return properties;
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
}
