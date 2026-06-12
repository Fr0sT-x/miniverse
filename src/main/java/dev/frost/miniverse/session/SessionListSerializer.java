package dev.frost.miniverse.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapStore;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameRegistry;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class SessionListSerializer {

    public static void broadcastSessionList(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (SessionPermissions.canManageSessions(player)) {
                sendSessionList(server, player);
            }
        }
    }

    public static void sendSessionList(MinecraftServer server, ServerPlayerEntity player) {
        NbtCompound root = new NbtCompound();
        NbtList sessions = new NbtList();

        if (SessionRuntimeConfig.isSessionServer()) {
            SessionRuntimeConfig.getSessionJson()
                .map(SessionListSerializer::runtimeSessionToNbt)
                .ifPresent(sessions::add);
        }

        List<SessionRegistry.Snapshot> snapshots = sessions.isEmpty() ? SessionRegistry.loadSnapshots() : List.of();
        if (snapshots.isEmpty()) {
            for (GameSession session : sessions.isEmpty() ? SessionManager.getInstance().getSessions() : List.<GameSession>of()) {
                NbtCompound entry = new NbtCompound();
                entry.putString("id", session.getSessionId());
                entry.putString("game", session.getGameType().getDisplayName());
                entry.putString("state", session.getState().name());
                entry.putLong("seed", session.getSeedPlan().sharedSeed());
                entry.putLong("createdAt", session.getCreatedAt().toEpochMilli());
                entry.putLong("launchedAt", session.getLaunchedAt() == null ? 0L : session.getLaunchedAt().toEpochMilli());
                entry.putLong("updatedAt", System.currentTimeMillis());
                entry.putLong("playedMillis", session.getLaunchedAt() == null ? 0L : Math.max(0L, System.currentTimeMillis() - session.getLaunchedAt().toEpochMilli()));
                entry.putBoolean("inspectable", false);
                entry.putBoolean("retained", false);

                NbtList players = new NbtList();
                session.getAssignments().forEach(assignment -> players.add(NbtString.of(assignment.getDisplayName())));
                entry.put("players", players);
                entry.putInt("playerCount", session.getAssignments().stream().mapToInt(SessionGroup::getPlayerCount).sum());
                addGroups(entry, session.getAssignments());
                sessions.add(entry);
            }
        } else {
            for (SessionRegistry.Snapshot snapshot : snapshots) {
                NbtCompound entry = new NbtCompound();
                entry.putString("id", snapshot.sessionId());
                entry.putString("game", snapshot.game());
                entry.putString("state", snapshot.state());
                entry.putLong("seed", snapshot.seed());
                entry.putLong("createdAt", snapshot.createdAtMillis());
                entry.putLong("launchedAt", snapshot.launchedAtMillis());
                entry.putLong("updatedAt", snapshot.updatedAtMillis());
                entry.putLong("playedMillis", snapshot.playedMillis());
                entry.putBoolean("inspectable", snapshot.inspectable());
                entry.putBoolean("retained", SessionManager.getInstance().getSession(snapshot.sessionId()).isEmpty());

                NbtList players = new NbtList();
                for (String name : snapshot.players()) {
                    players.add(NbtString.of(name));
                }
                entry.put("players", players);
                entry.putInt("playerCount", snapshot.playerCount());
                SessionManager.getInstance().getSession(snapshot.sessionId())
                    .ifPresent(session -> addGroups(entry, session.getAssignments()));
                sessions.add(entry);
            }
        }

        root.put("sessions", sessions);

        NbtList games = new NbtList();
        for (MinigameDefinition definition : MinigameRegistry.getDefinitions()) {
            games.add(definition.metadata().toNbt());
        }
        root.put("games", games);
        root.put("maps", MapStore.mapsToNbt());

        NbtList duelTypes = new NbtList();
        for (dev.frost.miniverse.minigame.impl.duels.DuelType dt : dev.frost.miniverse.minigame.impl.duels.DuelTypeRegistry.getAll()) {
            NbtCompound dtNbt = new NbtCompound();
            dtNbt.putString("id", dt.id());
            dtNbt.putString("name", dt.name());
            dtNbt.putBoolean("knockbackOnly", dt.knockbackOnly());
            dtNbt.putBoolean("allowBuilding", dt.allowBuilding());
            dtNbt.putBoolean("allowBreaking", dt.allowBreaking());
            dtNbt.putBoolean("allowHunger", dt.allowHunger());
            dtNbt.putBoolean("naturalRegen", dt.naturalRegen());
            duelTypes.add(dtNbt);
        }
        root.put("duelTypes", duelTypes);

        NbtList roster = new NbtList();
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("uuid", online.getUuidAsString());
            entry.putString("name", online.getName().getString());
            roster.add(entry);
        }
        root.put("players", roster);

        NbtList pendingJoiners = new NbtList();
        List<PendingSessionJoinManager.PendingJoiner> pending = SessionRuntimeConfig.isSessionServer()
            ? SessionRuntimeConfig.getSessionId()
                .map(sessionId -> SessionRegistry.listPendingJoinNotices(sessionId).stream()
                    .map(notice -> new PendingSessionJoinManager.PendingJoiner(notice.playerId(), notice.playerName(), notice.joinedAtMillis()))
                    .toList())
                .orElse(List.of())
            : PendingSessionJoinManager.getInstance().list(server);
        for (PendingSessionJoinManager.PendingJoiner pendingJoiner : pending) {
            NbtCompound entry = new NbtCompound();
            entry.putString("uuid", pendingJoiner.playerId().toString());
            entry.putString("name", pendingJoiner.playerName());
            entry.putLong("joinedAt", pendingJoiner.joinedAtMillis());
            pendingJoiners.add(entry);
        }
        root.put("pendingJoiners", pendingJoiners);

        NbtCompound launcher = new NbtCompound();
        SessionLauncherConfig launcherConfig = SessionLauncherConfig.getInstance();
        launcher.putInt("maxConcurrentLaunches", launcherConfig.maxConcurrentLaunches());
        launcher.putInt("queueCapacity", launcherConfig.queueCapacity());
        launcher.putInt("sessionPortStart", launcherConfig.sessionPortStart());
        launcher.putInt("sessionPortEnd", launcherConfig.sessionPortEnd());
        launcher.putInt("publicSessionPortStart", launcherConfig.publicSessionPortStart());
        launcher.putInt("publicSessionPortEnd", launcherConfig.publicSessionPortEnd());
        root.put("launcher", launcher);

        NbtCompound memory = new NbtCompound();
        SessionMemoryConfig memoryConfig = SessionMemoryConfig.getInstance();
        memory.putBoolean("enabled", memoryConfig.isEnabled());
        memory.putInt("maxHeapGb", memoryConfig.getMaxHeap());
        memory.putInt("initialHeapGb", memoryConfig.getInitialHeap());
        root.put("memory", memory);

        NbtCompound serverSettings = new NbtCompound();
        SessionServerConfig serverConfig = SessionServerConfig.getInstance();
        serverSettings.putInt("viewDistance", serverConfig.viewDistance());
        serverSettings.putInt("simulationDistance", serverConfig.simulationDistance());
        serverSettings.putBoolean("onlineMode", serverConfig.onlineMode());
        serverSettings.putInt("spawnProtection", serverConfig.spawnProtection());
        serverSettings.putString("difficulty", serverConfig.difficulty());
        serverSettings.putBoolean("allowFlight", serverConfig.allowFlight());
        serverSettings.putBoolean("acceptsTransfers", serverConfig.acceptsTransfers());
        serverSettings.putString("advertisedHost", serverConfig.advertisedHost());
        root.put("server", serverSettings);

        NbtCompound retention = new NbtCompound();
        SessionRetentionConfig retentionConfig = SessionRetentionConfig.getInstance();
        retention.putInt("maxAgeDays", retentionConfig.maxAgeDays());
        root.put("retention", retention);

        root.putBoolean("sessionServer", SessionRuntimeConfig.isSessionServer());
        root.putBoolean("mapEditor", Boolean.getBoolean("miniverse.mapEditor"));
        root.put("mapEditorExtensions", dev.frost.miniverse.map.editor.MapEditorNbt.extensionsToNbt());
        if (Boolean.getBoolean("miniverse.mapEditor")) {
            SessionRuntimeConfig.getSessionJson().ifPresent(json -> {
                String mapId = "";
                if (json.has("mapEditor") && json.get("mapEditor").isJsonObject()) {
                    mapId = dev.frost.miniverse.session.SessionConfigJson.string(json.getAsJsonObject("mapEditor"), "mapId", "");
                }
                root.put("mapEditorState", dev.frost.miniverse.map.editor.MapEditorNbt.editorStateToNbt(mapId));
            });
        }

        ServerPlayNetworking.send(player, new NetworkConstants.SessionListPayload(root));
    }

    private static NbtCompound runtimeSessionToNbt(JsonObject json) {
        NbtCompound entry = new NbtCompound();
        String sessionId = SessionConfigJson.string(json, "sessionId", "");
        entry.putString("id", sessionId);
        entry.putString("game", SessionConfigJson.string(json, "gameDisplayName", SessionConfigJson.string(json, "gameId", "")));
        entry.putString("state", SessionRegistry.isPauseRequested(sessionId) ? "PAUSED" : "RUNNING");
        entry.putLong("seed", SessionConfigJson.longValue(json, "seed", 0L));
        entry.putLong("createdAt", SessionConfigJson.longValue(json, "createdAt", 0L));
        entry.putLong("launchedAt", SessionConfigJson.longValue(json, "launchedAt", 0L));
        entry.putLong("updatedAt", System.currentTimeMillis());
        entry.putLong("playedMillis", Math.max(0L, System.currentTimeMillis() - SessionConfigJson.longValue(json, "launchedAt", System.currentTimeMillis())));
        entry.putBoolean("inspectable", false);
        entry.putBoolean("retained", false);
        entry.putInt("playerCount", runtimePlayerCount(json));
        entry.put("groups", runtimeGroups(json));
        return entry;
    }

    private static void addGroups(NbtCompound entry, List<SessionGroup> groups) {
        NbtList groupList = new NbtList();
        for (SessionGroup group : groups) {
            NbtCompound groupNbt = new NbtCompound();
            groupNbt.putString("label", group.getGroupLabel());
            groupNbt.putString("displayName", group.getDisplayName());
            groupNbt.putString("state", group.getState().name());
            groupNbt.putInt("playerCount", group.getPlayerCount());
            groupList.add(groupNbt);
        }
        entry.put("groups", groupList);
    }

    private static NbtList runtimeGroups(JsonObject json) {
        JsonArray teams = json.has("teams") && json.get("teams").isJsonArray()
            ? json.getAsJsonArray("teams")
            : new JsonArray();
        NbtList groupList = new NbtList();
        for (var teamElement : teams) {
            if (!teamElement.isJsonObject()) {
                continue;
            }
            JsonObject team = teamElement.getAsJsonObject();
            NbtCompound groupNbt = new NbtCompound();
            String label = SessionConfigJson.string(team, "label", "");
            String displayName = SessionConfigJson.string(team, "displayName", label);
            groupNbt.putString("label", label);
            groupNbt.putString("displayName", displayName);
            groupNbt.putString("state", SessionConfigJson.string(team, "state", "RUNNING"));
            groupNbt.putInt("playerCount", team.has("members") && team.get("members").isJsonArray()
                ? team.getAsJsonArray("members").size()
                : SessionConfigJson.integer(team, "playerCount", 0));
            groupList.add(groupNbt);
        }
        return groupList;
    }

    private static int runtimePlayerCount(JsonObject json) {
        JsonArray teams = json.has("teams") && json.get("teams").isJsonArray()
            ? json.getAsJsonArray("teams")
            : new JsonArray();

        int count = 0;
        for (var teamElement : teams) {
            if (!teamElement.isJsonObject()) {
                continue;
            }
            JsonObject team = teamElement.getAsJsonObject();
            if (team.has("members") && team.get("members").isJsonArray()) {
                count += team.getAsJsonArray("members").size();
            } else {
                count += SessionConfigJson.integer(team, "playerCount", 0);
            }
        }
        return count;
    }
}
