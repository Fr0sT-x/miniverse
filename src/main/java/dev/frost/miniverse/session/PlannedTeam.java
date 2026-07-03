package dev.frost.miniverse.session;

import dev.frost.miniverse.team.TeamColorPalette;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlannedTeam(String label, String gameTeamId, String displayName, List<SessionMembership> members) {
    public PlannedTeam(String label, List<SessionMembership> members) {
        this(label, "", "", members);
    }

    public PlannedTeam {
        label = label == null || label.isBlank() ? "Team" : label.trim();
        gameTeamId = gameTeamId == null ? "" : gameTeamId.trim();
        displayName = displayName == null || displayName.isBlank() ? TeamColorPalette.displayName(label) : displayName.trim();
        members = members == null ? List.of() : List.copyOf(deduplicate(members));
        if (members.isEmpty()) {
            throw new IllegalArgumentException("Planned teams must contain at least one member.");
        }
    }

    public List<UUID> playerUuids() {
        return this.members.stream().map(SessionMembership::playerUuid).toList();
    }

    public List<String> playerNames() {
        return this.members.stream().map(SessionMembership::playerName).toList();
    }

    public int playerCount() {
        return this.members.size();
    }

    public boolean containsPlayer(UUID playerUuid) {
        return this.members.stream().anyMatch(member -> member.playerUuid().equals(playerUuid));
    }

    public boolean hasGameTeamId() {
        return !this.gameTeamId.isBlank();
    }

    public PlannedTeam withMembers(List<SessionMembership> members) {
        return new PlannedTeam(this.label, this.gameTeamId, this.displayName, members);
    }

    private static List<SessionMembership> deduplicate(List<SessionMembership> memberships) {
        Map<UUID, SessionMembership> unique = new LinkedHashMap<>();
        for (SessionMembership membership : memberships) {
            if (membership != null) {
                unique.put(membership.playerUuid(), membership);
            }
        }
        return List.copyOf(unique.values());
    }
}
