package dev.frost.miniverse.minigame.core;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class ClientReadinessCoordinator {
    private static final int CLIENT_READY_TIMEOUT_TICKS = 60 * 20;

    private final Map<UUID, ClientReadyState> clientReadyStates = new HashMap<>();
    private final Map<UUID, Integer> loadingStartTicks = new HashMap<>();
    private final SessionConfigParser configParser;
    private final String gameId;

    public enum ClientReadyState {
        LOADING,
        READY
    }

    public ClientReadinessCoordinator(SessionConfigParser configParser, String gameId) {
        this.configParser = configParser;
        this.gameId = gameId;
    }

    public void markLoading(ServerPlayerEntity player) {
        this.clientReadyStates.put(player.getUuid(), ClientReadyState.LOADING);
        MinecraftServer server = player.getEntityWorld().getServer();
        this.loadingStartTicks.put(player.getUuid(), server == null ? 0 : server.getTicks());
        FreezeService.getInstance().freeze(player, FreezeReason.MATCH_LOADING);
    }

    public void markClientReady(UUID uuid) {
        this.clientReadyStates.put(uuid, ClientReadyState.READY);
        this.loadingStartTicks.remove(uuid);
    }

    public void releaseLoadedPlayer(ServerPlayerEntity player, Properties properties) {
        FreezeService.getInstance().unfreeze(player, FreezeReason.MATCH_LOADING);
        if (ServerPlayNetworking.canSend(player, NetworkConstants.MATCH_START_ID)) {
            ServerPlayNetworking.send(player, new NetworkConstants.MatchStartPayload(properties.getProperty("sessionId", "")));
        }
    }

    public boolean areExpectedPlayersReady(Properties properties) {
        for (UUID uuid : this.configParser.getExpectedPlayerIds(properties)) {
            if (this.clientReadyStates.get(uuid) != ClientReadyState.READY) {
                return false;
            }
        }
        return true;
    }

    public boolean checkReadyTimeouts(MinecraftServer server, Properties properties, Runnable abortCallback) {
        int now = server.getTicks();
        for (UUID uuid : this.configParser.getExpectedPlayerIds(properties)) {
            if (this.clientReadyStates.get(uuid) == ClientReadyState.READY) {
                continue;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null || player.isDisconnected()) {
                continue;
            }

            int started = this.loadingStartTicks.computeIfAbsent(uuid, ignored -> now);
            if (now - started < CLIENT_READY_TIMEOUT_TICKS) {
                continue;
            }

            Miniverse.LOGGER.warn("Disconnecting {} from session {} after client ready timeout.", player.getName().getString(), properties.getProperty("sessionId", ""));
            player.networkHandler.disconnect(Text.literal("Timed out while loading match resources. Please rejoin the session."));
            abortCallback.run();
            return true;
        }
        return false;
    }

    public void clear() {
        this.clientReadyStates.clear();
        this.loadingStartTicks.clear();
    }

    public void unfreezeLoadingPlayers(MinecraftServer server, Properties properties) {
        if (server == null) {
            return;
        }
        for (UUID uuid : this.configParser.getExpectedPlayerIds(properties)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                FreezeService.getInstance().unfreeze(player, FreezeReason.MATCH_LOADING);
            }
        }
    }

    public void sendMatchIntro(ServerPlayerEntity player, Properties properties, MatchLifecycleOptions options) {
        if (!ServerPlayNetworking.canSend(player, NetworkConstants.MATCH_INTRO_ID)) {
            return;
        }
        ServerPlayNetworking.send(player, new NetworkConstants.MatchIntroPayload(this.matchIntroData(properties, options)));
    }

    public void broadcastReadyState(MinecraftServer server, Properties properties, String status) {
        if (server == null) {
            return;
        }
        String sessionId = properties.getProperty("sessionId", "");
        int ready = this.readyCount(properties);
        int total = this.configParser.getExpectedPlayerIds(properties).size();
        for (UUID uuid : this.configParser.getExpectedPlayerIds(properties)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null && ServerPlayNetworking.canSend(player, NetworkConstants.MATCH_READY_STATE_ID)) {
                NbtCompound data = new NbtCompound();
                data.put("teams", this.teamIntroData(properties));
                ServerPlayNetworking.send(player, new NetworkConstants.MatchReadyStatePayload(sessionId, ready, total, status, data));
            }
        }
    }

    private NbtCompound matchIntroData(Properties properties, MatchLifecycleOptions options) {
        String configuredGameId = properties.getProperty("game", this.gameId);
        NbtCompound data = new NbtCompound();
        data.putString("sessionId", properties.getProperty("sessionId", ""));
        data.putString("gameId", configuredGameId);
        data.putString("title", options.startTitle().getString());
        data.putString("description", options.startSubtitle().getString());
        data.putString("map", properties.getProperty("map", properties.getProperty("world", "Generated Arena")));
        data.putInt("readyPlayers", this.readyCount(properties));
        data.putInt("totalPlayers", this.configParser.getExpectedPlayerIds(properties).size());
        data.put("teams", this.teamIntroData(properties));
        return data;
    }

    private NbtList teamIntroData(Properties properties) {
        Map<String, NbtCompound> teams = new LinkedHashMap<>();
        for (UUID uuid : this.configParser.getExpectedPlayerIds(properties)) {
            String teamId = this.configParser.getRoleTeamId(properties, this.gameId, uuid);
            NbtCompound team = teams.computeIfAbsent(teamId, id -> {
                NbtCompound created = new NbtCompound();
                created.putString("id", id);
                created.putString("name", SessionConfigParser.getDisplayName(id));
                created.putInt("color", SessionConfigParser.getTeamColor(id));
                created.put("players", new NbtList());
                return created;
            });

            NbtCompound member = new NbtCompound();
            member.putString("uuid", uuid.toString());
            member.putString("name", this.configParser.getPlayerName(properties, uuid));
            member.putBoolean("ready", this.clientReadyStates.get(uuid) == ClientReadyState.READY);
            team.getList("players", NbtElement.COMPOUND_TYPE).add(member);
        }

        NbtList list = new NbtList();
        teams.values().forEach(list::add);
        return list;
    }

    private int readyCount(Properties properties) {
        int ready = 0;
        for (UUID uuid : this.configParser.getExpectedPlayerIds(properties)) {
            if (this.clientReadyStates.get(uuid) == ClientReadyState.READY) {
                ready++;
            }
        }
        return ready;
    }
}
