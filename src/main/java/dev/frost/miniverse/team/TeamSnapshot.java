package dev.frost.miniverse.team;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.UUID;

public record TeamSnapshot(String id, String label, @org.jetbrains.annotations.Nullable net.minecraft.util.Formatting color, List<TeamMembership> members) {
    public TeamSnapshot {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Team snapshot id cannot be blank.");
        }
        label = label == null || label.isBlank() ? id : label.trim();
        members = members == null ? List.of() : List.copyOf(members);
    }

    public boolean contains(UUID playerUuid) {
        return this.members.stream().anyMatch(member -> member.playerUuid().equals(playerUuid));
    }

    public List<UUID> playerUuids() {
        return this.members.stream().map(TeamMembership::playerUuid).toList();
    }

    public List<ServerPlayerEntity> liveMembers(MinecraftServer server) {
        return this.members.stream()
            .map(member -> server.getPlayerManager().getPlayer(member.playerUuid()))
            .filter(java.util.Objects::nonNull)
            .toList();
    }
}
