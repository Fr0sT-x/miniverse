package dev.frost.miniverse.chat;

/**
 * Opt-in interface for minigames that support team/global chat routing via {@link ChatRouter}.
 * Gamemodes not implementing this interface (or returning false from {@link #isChatRoutingEnabled()})
 * use normal vanilla Minecraft chat.
 */
public interface ChatRoutingAware {

    /**
     * Whether chat routing should currently be applied to participant messages.
     *
     * @return true if team/global chat routing is enabled, false for standard vanilla chat.
     */
    default boolean isChatRoutingEnabled() {
        return true;
    }
}
