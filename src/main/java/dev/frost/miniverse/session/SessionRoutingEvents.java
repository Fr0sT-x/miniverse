package dev.frost.miniverse.session;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class SessionRoutingEvents {
    private SessionRoutingEvents() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SessionManager manager = SessionManager.getInstance();
            manager.getSessionForPlayer(handler.player.getUuid())
                .flatMap(session -> session.getAssignment(handler.player.getUuid()))
                .filter(assignment -> assignment.getState() == SessionState.RUNNING)
                .ifPresent(assignment -> manager.transferPlayer(handler.player, assignment));
        });
    }
}



