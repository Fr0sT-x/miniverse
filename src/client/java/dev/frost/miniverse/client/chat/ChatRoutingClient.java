package dev.frost.miniverse.client.chat;

import dev.frost.miniverse.chat.ChatChannel;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ChatRoutingClient {
    private static boolean routingActive = false;
    private static ChatChannel currentChannel = ChatChannel.TEAM;

    private ChatRoutingClient() {
    }

    public static boolean isRoutingActive() {
        return routingActive;
    }

    public static void setRoutingActive(boolean active, String defaultChannelName) {
        routingActive = active;
        if (defaultChannelName != null && !defaultChannelName.isBlank()) {
            try {
                currentChannel = ChatChannel.valueOf(defaultChannelName);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static ChatChannel getCurrentChannel() {
        return currentChannel;
    }

    public static void setCurrentChannel(ChatChannel channel) {
        if (channel != null) {
            currentChannel = channel;
        }
    }

    public static void toggleChannel() {
        ChatChannel next = (currentChannel == ChatChannel.TEAM) ? ChatChannel.GLOBAL : ChatChannel.TEAM;
        setChannelAndSync(next);
    }

    public static void setChannelAndSync(ChatChannel channel) {
        if (channel == null) {
            return;
        }
        currentChannel = channel;
        if (ClientPlayNetworking.canSend(NetworkConstants.CHAT_CHANNEL_SYNC_ID)) {
            ClientPlayNetworking.send(new NetworkConstants.ChatChannelSyncPayload(channel.name()));
        }
    }

    public static void reset() {
        routingActive = false;
        currentChannel = ChatChannel.TEAM;
    }
}
