package dev.frost.miniverse.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.frost.miniverse.common.MiniversePaths;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import net.minecraft.nbt.NbtCompound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

public class SessionConfigBuilder {

    private final ServerLauncher launcher;

    public SessionConfigBuilder(ServerLauncher launcher) {
        this.launcher = launcher;
    }

    public void writeSessionConfig(Path workingDirectory, GameSession session, SessionGroup group, BackendLaunchMode launchMode) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("game", session.getGameType().getCommandName());
        properties.setProperty("sessionId", session.getSessionId());
        properties.setProperty("groupLabel", group.getGroupLabel());
        properties.setProperty("assignmentLabel", group.getGroupLabel());
        properties.setProperty("return.host", launcher.resolveReturnHost());
        properties.setProperty("return.port", Integer.toString(launcher.resolveReturnPort()));

        NbtCompound settings = session.getSettings();
        Properties settingsProperties = new Properties();
        this.definitionFor(session).ifPresent(definition -> definition.writeSessionProperties(settings, settingsProperties));
        if (settings.contains("mapId", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            settingsProperties.setProperty("map.id", settings.getString("mapId"));
        }
        properties.putAll(settingsProperties);

        List<SessionGroup> groupsForConfig = launcher.groupsForConfig(session, group);
        for (SessionGroup sessionGroup : groupsForConfig) {
            String teamLabel = sessionGroup.getGroupLabel();
            for (UUID playerUuid : sessionGroup.getPlayerUuids()) {
                properties.setProperty("player." + playerUuid, "true");
                properties.setProperty("player." + playerUuid + ".team", teamLabel);
            }
        }

        SessionConfigJson.write(
                workingDirectory.resolve("miniverse-session.json"),
                SessionConfigJson.runtimeSession(session, group, groupsForConfig, settingsProperties, properties.getProperty("return.host"), launcher.parsePort(properties.getProperty("return.port"), 25565), MiniversePaths.sessionsRoot(), launchMode)
        );
    }

    public void writeInspectionConfig(Path workingDirectory) throws IOException {
        JsonObject config = new JsonObject();
        config.addProperty("launchMode", BackendLaunchMode.INSPECTION_SESSION.name());
        config.addProperty("return.host", launcher.resolveReturnHost());
        config.addProperty("return.port", launcher.resolveReturnPort());
        config.addProperty("registry.sessionsRoot", MiniversePaths.sessionsRoot().toString());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(
            workingDirectory.resolve("miniverse-session.json"),
            gson.toJson(config),
            StandardCharsets.UTF_8
        );
    }

    public void writeMapEditorConfig(Path workingDirectory, dev.frost.miniverse.map.MapDescriptor map) throws IOException {
        JsonObject config = new JsonObject();
        config.addProperty("launchMode", BackendLaunchMode.MAP_EDITOR.name());
        config.addProperty("return.host", launcher.resolveReturnHost());
        config.addProperty("return.port", launcher.resolveReturnPort());
        config.add("returnServer", SessionConfigJson.returnServer(launcher.resolveReturnHost(), launcher.resolveReturnPort()));
        JsonObject editor = new JsonObject();
        editor.addProperty("mapId", map.metadata().id());
        editor.addProperty("mapName", map.metadata().name());
        editor.addProperty("mapFolder", map.folder().toAbsolutePath().normalize().toString());
        editor.addProperty("worldFolder", map.worldFolder().toAbsolutePath().normalize().toString());
        config.add("mapEditor", editor);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(
            workingDirectory.resolve("miniverse-session.json"),
            gson.toJson(config),
            StandardCharsets.UTF_8
        );
    }

    public void writeServerProperties(Path workingDirectory, GameSession session, SessionGroup group, int port) throws IOException {
        int assignedPlayers = launcher.groupsForConfig(session, group).stream()
                .mapToInt(SessionGroup::getPlayerCount)
                .sum();
        ServerPropertiesBuilder.create()
            .withDefaults(SessionServerConfig.getInstance())
            .withPort(port)
            .withSeed(session.getSeedPlan().sharedSeed())
            .withMaxPlayers(launcher.resolveSessionMaxPlayers(assignedPlayers))
            .withMotd(session.getGameType().getDisplayName() + " " + session.getSessionId() + " / " + group.getGroupLabel())
            .writeTo(workingDirectory.resolve("server.properties"));
    }

    public void writeInspectionServerProperties(Path workingDirectory, String sessionId, int port) throws IOException {
        ServerPropertiesBuilder.create()
            .withDefaults(SessionServerConfig.getInstance())
            .withPort(port)
            .override("allow-flight", "true")
            .override("difficulty", "peaceful")
            .override("enable-command-block", "false")
            .override("force-gamemode", "true")
            .override("gamemode", "spectator")
            .withMaxPlayers(4)
            .withMotd("Miniverse inspection copy for " + sessionId)
            .override("pvp", "false")
            .override("spawn-monsters", "false")
            .writeTo(workingDirectory.resolve("server.properties"));
    }

    public void writeMapEditorServerProperties(Path workingDirectory, String mapId, int port) throws IOException {
        ServerPropertiesBuilder.create()
            .withDefaults(SessionServerConfig.getInstance())
            .withPort(port)
            .override("allow-flight", "true")
            .override("difficulty", "peaceful")
            .override("force-gamemode", "true")
            .override("gamemode", "creative")
            .override("generate-structures", "false")
            .override("generator-settings", "{\"layers\":[],\"biome\":\"minecraft:the_void\"}")
            .withSeed(0)
            .override("level-type", "minecraft:flat")
            .withMaxPlayers(4)
            .withMotd("Miniverse map editor for " + mapId)
            .override("pvp", "false")
            .override("spawn-monsters", "false")
            .writeTo(workingDirectory.resolve("server.properties"));
    }

    private Optional<MinigameDefinition> definitionFor(GameSession session) {
        return Optional.of(session.getGameType().definition());
    }
}
