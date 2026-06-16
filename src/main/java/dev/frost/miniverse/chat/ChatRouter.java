package dev.frost.miniverse.chat;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamSnapshot;
import dev.frost.miniverse.team.TeamColorPalette;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class ChatRouter {
    public static final String GLOBAL_PREFIX = "!";
    private static final Text TEAM_CHAT_NOTICE = Text.literal(
        "You are now in Team Chat. Only players in your team can see your messages. To chat globally, start your message with !"
    );

    private ChatRouter() {
    }

    public static boolean handleChatMessage(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters parameters) {
        if (!dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().isMatchActive()) {
            return false;
        }
        if (!MinigameManager.getInstance().isParticipant(sender)) {
            return false;
        }

        String raw = message.getContent().getString();
        ChatChannel channel = resolveChannel(raw);
        String content = stripPrefix(raw, channel);
        if (content.isBlank()) {
            return true;
        }

        if (channel == ChatChannel.TEAM) {
            sendTeamChat(sender, content);
            return true;
        }

        sendGlobalChat(sender, content);
        return true;
    }

    public static void sendTeamChatNotice(Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            player.sendMessage(TEAM_CHAT_NOTICE, false);
        }
    }

    public static void notifyPlayerIfMatchActive(ServerPlayerEntity player) {
        if (!dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().isMatchActive()) {
            return;
        }
        if (!MinigameManager.getInstance().isParticipant(player)) {
            return;
        }
        player.sendMessage(TEAM_CHAT_NOTICE, false);
    }

    private static ChatChannel resolveChannel(String raw) {
        if (raw.startsWith(GLOBAL_PREFIX)) {
            return ChatChannel.GLOBAL;
        }
        return ChatChannel.TEAM;
    }

    private static String stripPrefix(String raw, ChatChannel channel) {
        if (channel == ChatChannel.GLOBAL && raw.startsWith(GLOBAL_PREFIX)) {
            return raw.substring(GLOBAL_PREFIX.length()).stripLeading();
        }
        return raw;
    }

    private static void sendTeamChat(ServerPlayerEntity sender, String content) {
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

    private static void sendGlobalChat(ServerPlayerEntity sender, String content) {
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
