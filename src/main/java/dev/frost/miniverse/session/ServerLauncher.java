package dev.frost.miniverse.session;

import dev.frost.miniverse.Miniverse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class ServerLauncher {
    public record LaunchResult(PlayerAssignment assignment, Process process, int port, Path workingDirectory) {
    }

    public LaunchResult launch(GameSession session, PlayerAssignment assignment) throws IOException {
        int port = this.reservePort();
        Path workingDirectory = this.prepareWorkingDirectory(session, assignment);
        this.writeEula(workingDirectory);
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
        this.appendArgument(content, "-Dfabric.dli.config=" + launchConfig);
        this.appendArgument(content, "-Dfabric.dli.env=server");
        this.appendArgument(content, "-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer");
        this.appendArgument(content, "-Dminiverse.session.port=" + port);
        this.appendArgument(content, "-Dminiverse.session.dir=" + workingDirectory.toAbsolutePath());
        if (session.getGameType() == SessionGameType.MANHUNT) {
            int respawnDelaySeconds = session.getSettings().getInt("speedrunnerRespawnDelaySeconds", 300);
            this.appendArgument(content, "-Dminiverse.manhunt.respawnDelaySeconds=" + Math.max(0, respawnDelaySeconds));
        }
        this.appendArgument(content, "-cp");
        this.appendArgument(content, classPath);
        this.appendArgument(content, "net.fabricmc.devlaunchinjector.Main");
        this.appendArgument(content, "nogui");

        Files.writeString(argumentsFile, content.toString(), StandardCharsets.UTF_8);
    }

    private void waitForPortOpen(Process process, int port) throws IOException {
        long deadline = System.currentTimeMillis() + 90_000L;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IOException("Launched server exited before opening port " + port + ".");
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 750);
                return;
            } catch (IOException ignored) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for server port " + port + ".", e);
                }
            }
        }

        throw new IOException("Timed out waiting for launched server to open port " + port + ".");
    }

    private Path prepareWorkingDirectory(GameSession session, PlayerAssignment assignment) throws IOException {
        Path sessionsRoot = this.locateProjectRoot().resolve("run").resolve("sessions");
        Path sessionDirectory = sessionsRoot.resolve(session.getSessionId()).resolve(this.sanitize(assignment.getAssignmentLabel()));
        Files.createDirectories(sessionDirectory);
        Files.createDirectories(sessionDirectory.resolve("logs"));
        return sessionDirectory;
    }

    private void writeEula(Path workingDirectory) throws IOException {
        Path eula = workingDirectory.resolve("eula.txt");
        Files.writeString(eula, "eula=true\n", StandardCharsets.UTF_8);
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
        properties.setProperty("max-players", Integer.toString(Math.max(1, assignment.getPlayerCount())));
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
        Files.writeString(workingDirectory.resolve("server.properties"), content.toString(), StandardCharsets.UTF_8);
    }

    private String resolveJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        Path javaBin = Paths.get(javaHome, "bin", this.isWindows() ? "java.exe" : "java");
        return javaBin.toString();
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
}




