package dev.frost.miniverse.chat;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Interface implemented by minigames that wish to inspect, intercept,
 * or suppress player chat messages (e.g. trivia or math microgames).
 */
public interface ChatInterceptAware {
    /**
     * Called when a participating player sends a chat message.
     * Return CONSUME_SILENT to suppress the message, or PASS to broadcast it normally.
     */
    ChatInterceptResult onChatMessage(ServerPlayerEntity sender, String message);
}
