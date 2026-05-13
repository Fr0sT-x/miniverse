package dev.frost.miniverse.team;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GameTeam {
    private final String id;
    private String label;
    private final Map<UUID, TeamMembership> members = new LinkedHashMap<>();

    GameTeam(String id, String label) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Game team id cannot be blank.");
        }
        this.id = sanitizeId(id);
        this.label = normalizeLabel(label, this.id);
    }

    public String id() {
        return this.id;
    }

    public String label() {
        return this.label;
    }

    public void setLabel(String label) {
        this.label = normalizeLabel(label, this.id);
    }

    public void add(ServerPlayerEntity player, TeamRole role) {
        this.add(TeamMembership.of(player, role));
    }

    public void add(TeamMembership membership) {
        this.members.put(membership.playerUuid(), membership);
    }

    public void remove(UUID playerUuid) {
        this.members.remove(playerUuid);
    }

    public boolean contains(UUID playerUuid) {
        return this.members.containsKey(playerUuid);
    }

    public TeamMembership membership(UUID playerUuid) {
        return this.members.get(playerUuid);
    }

    public int size() {
        return this.members.size();
    }

    public boolean isEmpty() {
        return this.members.isEmpty();
    }

    public List<TeamMembership> members() {
        return List.copyOf(this.members.values());
    }

    public TeamSnapshot snapshot() {
        return new TeamSnapshot(this.id, this.label, this.members());
    }

    static String sanitizeId(String value) {
        String sanitized = value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        return sanitized.isBlank() ? "team" : sanitized;
    }

    static String normalizeLabel(String label, String fallback) {
        return label == null || label.isBlank() ? fallback : label.trim();
    }
}
