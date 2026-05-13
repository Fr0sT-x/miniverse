package dev.frost.miniverse.team;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public record TeamMembership(UUID playerUuid, String playerName, TeamRole role) {
    public TeamMembership {
        if (playerUuid == null) {
            throw new IllegalArgumentException("Team membership player UUID cannot be null.");
        }
        playerName = playerName == null || playerName.isBlank() ? playerUuid.toString() : playerName.trim();
        role = role == null ? TeamRole.MEMBER : role;
    }

    public static TeamMembership of(ServerPlayerEntity player, TeamRole role) {
        return new TeamMembership(player.getUuid(), player.getName().getString(), role);
    }
}
