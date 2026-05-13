package dev.frost.miniverse.session;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public record SessionMembership(UUID playerUuid, String playerName, String role) {
    public SessionMembership {
        if (playerUuid == null) {
            throw new IllegalArgumentException("Session membership player UUID cannot be null.");
        }
        playerName = playerName == null || playerName.isBlank() ? playerUuid.toString() : playerName.trim();
        role = role == null ? "" : role.trim();
    }

    public static SessionMembership of(ServerPlayerEntity player) {
        return new SessionMembership(player.getUuid(), player.getName().getString(), "");
    }

    public boolean hasRole() {
        return !this.role.isBlank();
    }
}
