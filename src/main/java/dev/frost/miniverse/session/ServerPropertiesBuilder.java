package dev.frost.miniverse.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ServerPropertiesBuilder {
    private final Properties properties = new Properties();

    public static ServerPropertiesBuilder create() {
        return new ServerPropertiesBuilder();
    }

    public ServerPropertiesBuilder withDefaults(SessionServerConfig config) {
        properties.setProperty("accepts-transfers", Boolean.toString(config.acceptsTransfers()));
        properties.setProperty("allow-flight", Boolean.toString(config.allowFlight()));
        properties.setProperty("allow-nether", "true");
        properties.setProperty("broadcast-console-to-ops", "true");
        properties.setProperty("broadcast-rcon-to-ops", "true");
        properties.setProperty("difficulty", config.difficulty());
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
        properties.setProperty("level-type", "minecraft:normal");
        properties.setProperty("network-compression-threshold", "256");
        properties.setProperty("online-mode", Boolean.toString(config.onlineMode()));
        properties.setProperty("op-permission-level", "4");
        properties.setProperty("player-idle-timeout", "0");
        properties.setProperty("prevent-proxy-connections", "false");
        properties.setProperty("pvp", "true");
        properties.setProperty("rate-limit", "0");
        properties.setProperty("rcon.password", "");
        properties.setProperty("rcon.port", "25575");
        properties.setProperty("require-resource-pack", "false");
        properties.setProperty("resource-pack", "");
        properties.setProperty("resource-pack-prompt", "");
        properties.setProperty("server-ip", "");
        properties.setProperty("simulation-distance", Integer.toString(config.simulationDistance()));
        properties.setProperty("spawn-monsters", "true");
        properties.setProperty("spawn-protection", Integer.toString(config.spawnProtection()));
        properties.setProperty("sync-chunk-writes", "true");
        properties.setProperty("text-filtering-config", "");
        properties.setProperty("use-native-transport", "true");
        properties.setProperty("view-distance", Integer.toString(config.viewDistance()));
        properties.setProperty("white-list", "false");
        return this;
    }

    public ServerPropertiesBuilder withPort(int port) {
        properties.setProperty("server-port", Integer.toString(port));
        properties.setProperty("query.port", Integer.toString(port));
        return this;
    }

    public ServerPropertiesBuilder withSeed(long seed) {
        properties.setProperty("level-seed", Long.toString(seed));
        return this;
    }

    public ServerPropertiesBuilder withMaxPlayers(int maxPlayers) {
        properties.setProperty("max-players", Integer.toString(maxPlayers));
        return this;
    }

    public ServerPropertiesBuilder withMotd(String motd) {
        properties.setProperty("motd", motd);
        return this;
    }

    public ServerPropertiesBuilder override(String key, String value) {
        properties.setProperty(key, value);
        return this;
    }

    public void writeTo(Path file) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            content.append(key).append('=').append(properties.getProperty(key)).append('\n');
        }
        Files.writeString(file, content.toString(), StandardCharsets.UTF_8);
    }
}
