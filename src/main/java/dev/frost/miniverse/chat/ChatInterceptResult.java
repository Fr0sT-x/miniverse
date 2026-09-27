package dev.frost.miniverse.chat;

/**
 * Result returned by a minigame intercepting player chat messages.
 */
public enum ChatInterceptResult {
    /**
     * Consume the chat message silently.
     * The message is suppressed and not broadcast to chat.
     */
    CONSUME_SILENT,

    /**
     * Pass the chat message to normal chat channels (e.g. for wrong answers).
     */
    PASS
}
