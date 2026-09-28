package dev.frost.miniverse.chat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.team.TeamColorPalette;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamSnapshot;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public final class ChatRouter {
    public static final String GLOBAL_PREFIX = "!";
    private static final Text TEAM_CHAT_NOTICE = Text.literal(
        "§6[Miniverse] §eYou are in §bTeam Chat§e. Press §b[TAB]§e or toggle the chat button to switch to §aAll Chat§e (or use §a/a <msg>§e)."
    );

    private static final Map<UUID, ChatChannel> PLAYER_DEFAULT_CHANNELS = new ConcurrentHashMap<>();

    private ChatRouter() {
    }

    public static boolean isChatRoutingActive() {
        if (!MinigameManager.getInstance().getMatchLifecycleController().isMatchActive()) {
            return false;
        }
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        return active instanceof ChatRoutingAware routingAware && routingAware.isChatRoutingEnabled();
    }

    public static ChatChannel getPlayerChannel(UUID uuid) {
        return PLAYER_DEFAULT_CHANNELS.getOrDefault(uuid, ChatChannel.TEAM);
    }

    public static void setPlayerChannel(UUID uuid, ChatChannel channel) {
        PLAYER_DEFAULT_CHANNELS.put(uuid, channel);
    }

    public static void setPlayerChannel(ServerPlayerEntity player, ChatChannel channel) {
        PLAYER_DEFAULT_CHANNELS.put(player.getUuid(), channel);
        if (ServerPlayNetworking.canSend(player, NetworkConstants.CHAT_CHANNEL_SYNC_ID)) {
            ServerPlayNetworking.send(player, new NetworkConstants.ChatChannelSyncPayload(channel.name()));
        }
    }

    public static void clearPlayerChannels() {
        PLAYER_DEFAULT_CHANNELS.clear();
    }

    public static void syncRoutingStateToPlayer(ServerPlayerEntity player) {
        boolean active = isChatRoutingActive();
        ChatChannel channel = active ? getPlayerChannel(player.getUuid()) : ChatChannel.GLOBAL;
        if (ServerPlayNetworking.canSend(player, NetworkConstants.CHAT_ROUTING_SYNC_ID)) {
            ServerPlayNetworking.send(player, new NetworkConstants.ChatRoutingSyncPayload(active, channel.name()));
        }
    }

    public static void syncRoutingStateToRoster(Collection<ServerPlayerEntity> players, boolean active) {
        if (!active) {
            clearPlayerChannels();
        }
        for (ServerPlayerEntity player : players) {
            ChatChannel channel = active ? getPlayerChannel(player.getUuid()) : ChatChannel.GLOBAL;
            if (ServerPlayNetworking.canSend(player, NetworkConstants.CHAT_ROUTING_SYNC_ID)) {
                ServerPlayNetworking.send(player, new NetworkConstants.ChatRoutingSyncPayload(active, channel.name()));
            }
        }
    }

    public static boolean handleChatMessage(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters parameters) {
        if (!isChatRoutingActive()) {
            return false;
        }
        if (!MinigameManager.getInstance().isParticipant(sender)) {
            return false;
        }

        String raw = message.getContent().getString();
        ChatChannel channel;
        String content;
        if (raw.startsWith(GLOBAL_PREFIX)) {
            channel = ChatChannel.GLOBAL;
            content = raw.substring(GLOBAL_PREFIX.length()).stripLeading();
        } else {
            channel = getPlayerChannel(sender.getUuid());
            content = raw;
        }

        if (content.isBlank()) {
            return true;
        }

        return dispatchMessage(sender, content, channel);
    }

    public static boolean dispatchMessage(ServerPlayerEntity sender, String content, ChatChannel channel) {
        if (content.isBlank()) {
            return true;
        }

        // Check if active minigame intercepts chat messages (e.g. Quick Math)
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof ChatInterceptAware interceptAware) {
            ChatInterceptResult result = interceptAware.onChatMessage(sender, content);
            if (result == ChatInterceptResult.CONSUME_SILENT) {
                return true; // Suppressed silently!
            }
        }

        if (channel == ChatChannel.TEAM) {
            sendTeamChat(sender, content);
        } else {
            sendGlobalChat(sender, content);
        }
        return true;
    }

    public static void sendTeamChatNotice(Collection<ServerPlayerEntity> players) {
        if (!isChatRoutingActive()) {
            return;
        }
        for (ServerPlayerEntity player : players) {
            player.sendMessage(TEAM_CHAT_NOTICE, false);
            syncRoutingStateToPlayer(player);
        }
    }

    public static void notifyPlayerIfMatchActive(ServerPlayerEntity player) {
        if (!isChatRoutingActive()) {
            return;
        }
        if (!MinigameManager.getInstance().isParticipant(player)) {
            return;
        }
        player.sendMessage(TEAM_CHAT_NOTICE, false);
        syncRoutingStateToPlayer(player);
    }

    public static void sendTeamChat(ServerPlayerEntity sender, String content) {
        TeamManager teamManager = resolveTeamManager();
        if (!(sender.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        MinecraftServer server = serverWorld.getServer();
        String teamId = teamManager == null ? null : teamManager.teamId(sender.getUuid());
        Text formatted = formatMessage(ChatChannel.TEAM, sender, content, teamId);
        if (teamId == null || teamManager == null) {
            for (ServerPlayerEntity player : MinigameManager.getInstance().getParticipants()) {
                player.sendMessage(formatted, false);
            }
            return;
        }
        List<TeamSnapshot> snapshots = teamManager.snapshots(List.of(teamId));
        if (snapshots.isEmpty()) {
            for (ServerPlayerEntity player : MinigameManager.getInstance().getParticipants()) {
                player.sendMessage(formatted, false);
            }
            return;
        }
        TeamSnapshot snapshot = snapshots.get(0);
        for (ServerPlayerEntity player : snapshot.liveMembers(server)) {
            player.sendMessage(formatted, false);
        }
    }

    public static void sendGlobalChat(ServerPlayerEntity sender, String content) {
        if (!(sender.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        MinecraftServer server = serverWorld.getServer();
        Text formatted = formatMessage(ChatChannel.GLOBAL, sender, content, null);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(formatted, false);
        }
    }

    private static Text formatMessage(ChatChannel channel, ServerPlayerEntity sender, String content, @Nullable String teamId) {
        Text name;
        if (channel == ChatChannel.TEAM) {
            name = sender.getName();
            if (teamId != null && !teamId.isBlank()) {
                name = name.copy().formatted(TeamColorPalette.colorFor(teamId));
            }
        } else {
            name = sender.getDisplayName();
        }
        return Text.literal(channel.prefix() + " ")
            .append(name)
            .append(Text.literal(": " + content));
    }

    @Nullable
    private static TeamManager resolveTeamManager() {
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime == null) {
            return null;
        }
        if (!(runtime.minigame() instanceof TeamManagerProvider provider)) {
            return null;
        }
        return provider.teamManager();
    }
}
