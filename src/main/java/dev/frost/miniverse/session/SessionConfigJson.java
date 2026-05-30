package dev.frost.miniverse.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.Miniverse;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Collection;

public final class SessionConfigJson {
    public static final int VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SessionConfigJson() {
    }

    public static JsonObject baseSession(GameSession session) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("sessionId", session.getSessionId());
        root.addProperty("gameId", session.getGameType().getCommandName());
        root.addProperty("gameDisplayName", session.getGameType().getDisplayName());
        root.addProperty("topology", session.getGameType().getTopology().name());
        root.addProperty("state", session.getState().name());
        root.addProperty("seed", session.getSeedPlan().sharedSeed());
        root.addProperty("settingsNbt", session.getSettings().toString());
        root.addProperty("createdAt", session.getCreatedAt().toEpochMilli());
        if (session.getLaunchedAt() != null) {
            root.addProperty("launchedAt", session.getLaunchedAt().toEpochMilli());
        }
        root.add("lifecycle", lifecycle(false, false));
        root.add("teams", teams(session));
        root.add("backendAssignments", backendAssignments(session));
        return root;
    }

    public static JsonObject runtimeSession(GameSession session, SessionGroup group, Collection<SessionGroup> assignedGroups, Properties gameProperties, String returnHost, int returnPort, Path mainSessionsRoot) {
        return runtimeSession(session, group, assignedGroups, gameProperties, returnHost, returnPort, mainSessionsRoot, BackendLaunchMode.NEW_SESSION);
    }

    public static JsonObject runtimeSession(GameSession session, SessionGroup group, Collection<SessionGroup> assignedGroups, Properties gameProperties, String returnHost, int returnPort, Path mainSessionsRoot, BackendLaunchMode launchMode) {
        JsonObject root = baseSession(session);
        root.addProperty("launchMode", (launchMode == null ? BackendLaunchMode.NEW_SESSION : launchMode).name());
        root.addProperty("groupLabel", group.getGroupLabel());
        root.addProperty("assignmentLabel", group.getGroupLabel());
        root.add("teams", teams(assignedGroups));
        root.add("backendAssignments", backendAssignments(assignedGroups));
        root.add("settings", nestedProperties(gameProperties));
        root.add("returnServer", returnServer(returnHost, returnPort));
        root.add("registry", registry(mainSessionsRoot));
        root.add("proxy", proxy(VelocityProxyConfig.getInstance()));
        return root;
    }

    public static JsonObject registry(Path mainSessionsRoot) {
        JsonObject registry = new JsonObject();
        if (mainSessionsRoot != null) {
            registry.addProperty("sessionsRoot", mainSessionsRoot.toAbsolutePath().normalize().toString());
        }
        return registry;
    }

    public static JsonObject lifecycle(boolean stopRequested, boolean returnComplete) {
        return lifecycle(stopRequested, returnComplete, false);
    }

    public static JsonObject lifecycle(boolean stopRequested, boolean returnComplete, boolean seedChangeRequested) {
        return lifecycle(stopRequested, returnComplete, seedChangeRequested, false);
    }

    public static JsonObject lifecycle(boolean stopRequested, boolean returnComplete, boolean seedChangeRequested, boolean pauseRequested) {
        JsonObject lifecycle = new JsonObject();
        lifecycle.addProperty("stopRequested", stopRequested);
        lifecycle.addProperty("returnComplete", returnComplete);
        lifecycle.addProperty("seedChangeRequested", seedChangeRequested);
        lifecycle.addProperty("pauseRequested", pauseRequested);
        return lifecycle;
    }

    public static JsonObject returnServer(String host, int port) {
        JsonObject returnServer = new JsonObject();
        returnServer.addProperty("host", host == null || host.isBlank() ? "127.0.0.1" : host);
        returnServer.addProperty("port", port <= 0 ? 25565 : port);
        return returnServer;
    }

    public static JsonObject proxy(VelocityProxyConfig config) {
        JsonObject proxy = new JsonObject();
        if (config != null) {
            proxy.addProperty("velocityEnabled", config.velocityEnabled());
            proxy.addProperty("lobbyServerName", config.lobbyServerName());
            proxy.addProperty("backendHost", config.backendHost());
            proxy.addProperty("serverNamePrefix", config.serverNamePrefix());
        }
        return proxy;
    }

    public static JsonArray teams(GameSession session) {
        return teams(session.snapshotGroups());
    }

    public static JsonArray teams(Collection<SessionGroup> groups) {
        JsonArray teams = new JsonArray();
        for (SessionGroup group : groups) {
            JsonObject team = new JsonObject();
            team.addProperty("label", group.getGroupLabel());
            team.addProperty("displayName", group.getDisplayName());
            team.addProperty("state", group.getState().name());
            team.addProperty("playerCount", group.getPlayerCount());

            JsonArray members = new JsonArray();
            for (SessionMembership member : group.getPlannedTeam().members()) {
                JsonObject player = new JsonObject();
                player.addProperty("uuid", member.playerUuid().toString());
                player.addProperty("name", member.playerName());
                if (member.hasRole()) {
                    player.addProperty("role", member.role());
                }
                members.add(player);
            }
            team.add("members", members);
            teams.add(team);
        }
        return teams;
    }

    public static JsonArray backendAssignments(GameSession session) {
        return backendAssignments(session.snapshotGroups());
    }

    public static JsonArray backendAssignments(Collection<SessionGroup> groups) {
        JsonArray assignments = new JsonArray();
        for (SessionGroup group : groups) {
            JsonObject assignment = new JsonObject();
            assignment.addProperty("label", group.getGroupLabel());
            assignment.addProperty("displayName", group.getDisplayName());
            assignment.addProperty("state", group.getState().name());
            if (group.getPort() != null) {
                assignment.addProperty("port", group.getPort());
            }
            if (group.getConnectionAddress() != null) {
                assignment.addProperty("address", group.getConnectionAddress());
            }
            if (group.getWorkingDirectory() != null) {
                assignment.addProperty("workingDirectory", group.getWorkingDirectory().toAbsolutePath().toString());
            }
            assignments.add(assignment);
        }
        return assignments;
    }

    public static JsonObject nestedProperties(Properties properties) {
        JsonObject root = new JsonObject();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            putNested(root, key, properties.getProperty(key));
        }
        return root;
    }

    public static void write(Path path, JsonObject json) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(json, writer);
        }
    }

    public static Optional<JsonObject> read(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element != null && element.isJsonObject()) {
                return Optional.of(element.getAsJsonObject());
            }
        } catch (IOException | IllegalStateException e) {
            Miniverse.LOGGER.warn("Failed to read Miniverse session JSON {}", path, e);
        }
        return Optional.empty();
    }

    public static Properties readRuntimeProperties(Path path) {
        Properties properties = new Properties();
        if (path.toString().endsWith(".json")) {
            read(path).ifPresent(json -> flattenRuntimeJson(json, properties));
            return properties;
        }

        if (!Files.isRegularFile(path)) {
            return properties;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to read Miniverse session properties {}", path, e);
        }
        return properties;
    }

    public static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    public static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        try {
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (NumberFormatException | IllegalStateException ignored) {
            return fallback;
        }
    }

    public static long longValue(JsonObject object, String key, long fallback) {
        JsonElement value = object.get(key);
        try {
            return value == null || value.isJsonNull() ? fallback : value.getAsLong();
        } catch (NumberFormatException | IllegalStateException ignored) {
            return fallback;
        }
    }

    public static boolean lifecycleFlag(JsonObject object, String key) {
        JsonObject lifecycle = object.has("lifecycle") && object.get("lifecycle").isJsonObject()
            ? object.getAsJsonObject("lifecycle")
            : object;
        JsonElement value = lifecycle.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    public static void flattenRuntimeJson(JsonObject json, Properties properties) {
        properties.setProperty("game", string(json, "gameId", string(json, "game", "")));
        properties.setProperty("sessionId", string(json, "sessionId", ""));
        properties.setProperty("launchMode", string(json, "launchMode", BackendLaunchMode.NEW_SESSION.name()));
        properties.setProperty("groupLabel", string(json, "groupLabel", ""));
        properties.setProperty("assignmentLabel", string(json, "assignmentLabel", properties.getProperty("groupLabel", "")));

        JsonObject returnServer = json.has("returnServer") && json.get("returnServer").isJsonObject()
            ? json.getAsJsonObject("returnServer")
            : new JsonObject();
        properties.setProperty("return.host", string(returnServer, "host", "127.0.0.1"));
        properties.setProperty("return.port", Integer.toString(integer(returnServer, "port", 25565)));

        JsonObject registry = json.has("registry") && json.get("registry").isJsonObject()
            ? json.getAsJsonObject("registry")
            : new JsonObject();
        String sessionsRoot = string(registry, "sessionsRoot", "");
        if (!sessionsRoot.isBlank()) {
            properties.setProperty("registry.sessionsRoot", sessionsRoot);
        }

        JsonObject proxy = json.has("proxy") && json.get("proxy").isJsonObject()
            ? json.getAsJsonObject("proxy")
            : new JsonObject();
        if (proxy.has("velocityEnabled")) {
            properties.setProperty("proxy.velocityEnabled", Boolean.toString(proxy.get("velocityEnabled").getAsBoolean()));
        }
        properties.setProperty("proxy.lobbyServerName", string(proxy, "lobbyServerName", ""));
        properties.setProperty("proxy.backendHost", string(proxy, "backendHost", ""));
        properties.setProperty("proxy.serverNamePrefix", string(proxy, "serverNamePrefix", ""));

        JsonElement settings = json.get("settings");
        if (settings != null && settings.isJsonObject()) {
            flattenJson("", settings.getAsJsonObject(), properties);
        }

        JsonElement teams = json.get("teams");
        if (teams != null && teams.isJsonArray()) {
            for (JsonElement teamElement : teams.getAsJsonArray()) {
                if (!teamElement.isJsonObject()) {
                    continue;
                }
                JsonObject team = teamElement.getAsJsonObject();
                String label = string(team, "label", properties.getProperty("groupLabel", "Team"));
                JsonElement members = team.get("members");
                if (members == null || !members.isJsonArray()) {
                    continue;
                }
                for (JsonElement memberElement : members.getAsJsonArray()) {
                    if (!memberElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject member = memberElement.getAsJsonObject();
                    String uuid = string(member, "uuid", "");
                    if (uuid.isBlank()) {
                        continue;
                    }
                    properties.setProperty("player." + uuid, "true");
                    properties.setProperty("player." + uuid + ".name", string(member, "name", ""));
                    properties.setProperty("player." + uuid + ".team", label);
                    String role = string(member, "role", "");
                    if (!role.isBlank()) {
                        properties.setProperty(properties.getProperty("game") + ".role." + uuid, role);
                    }
                }
            }
        }
    }

    private static void putNested(JsonObject root, String dottedKey, String value) {
        String[] parts = dottedKey.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            JsonElement child = current.get(part);
            if (child == null || !child.isJsonObject()) {
                JsonObject next = new JsonObject();
                current.add(part, next);
                current = next;
            } else {
                current = child.getAsJsonObject();
            }
        }
        current.addProperty(parts[parts.length - 1], value);
    }

    private static void flattenJson(String prefix, JsonObject object, Properties properties) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                flattenJson(key, value.getAsJsonObject(), properties);
            } else if (!value.isJsonArray() && !value.isJsonNull()) {
                properties.setProperty(key, value.getAsString());
            }
        }
    }
}
