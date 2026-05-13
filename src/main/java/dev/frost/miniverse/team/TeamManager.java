package dev.frost.miniverse.team;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TeamManager {
    private final Map<String, GameTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeamIds = new LinkedHashMap<>();

    public GameTeam ensureTeam(String id, String label) {
        String normalizedId = GameTeam.sanitizeId(id);
        GameTeam team = this.teams.computeIfAbsent(normalizedId, ignored -> new GameTeam(normalizedId, label));
        team.setLabel(label);
        return team;
    }

    public void assign(ServerPlayerEntity player, String teamId, String label) {
        this.assign(player, teamId, label, TeamRole.MEMBER);
    }

    public void assign(ServerPlayerEntity player, String teamId, String label, TeamRole role) {
        GameTeam team = this.ensureTeam(teamId, label);
        this.remove(player.getUuid());
        team.add(player, role);
        this.playerTeamIds.put(player.getUuid(), team.id());
    }

    public void assign(UUID playerUuid, String playerName, String teamId, String label, TeamRole role) {
        GameTeam team = this.ensureTeam(teamId, label);
        this.remove(playerUuid);
        team.add(new TeamMembership(playerUuid, playerName, role));
        this.playerTeamIds.put(playerUuid, team.id());
    }

    public void remove(ServerPlayerEntity player) {
        this.remove(player.getUuid());
    }

    public void remove(UUID playerUuid) {
        String teamId = this.playerTeamIds.remove(playerUuid);
        if (teamId == null) {
            return;
        }
        GameTeam team = this.teams.get(teamId);
        if (team != null) {
            team.remove(playerUuid);
        }
    }

    public void clear() {
        this.teams.clear();
        this.playerTeamIds.clear();
    }

    public boolean contains(UUID playerUuid) {
        return this.playerTeamIds.containsKey(playerUuid);
    }

    @Nullable
    public String teamId(UUID playerUuid) {
        return this.playerTeamIds.get(playerUuid);
    }

    public String teamLabel(UUID playerUuid, String fallback) {
        String teamId = this.playerTeamIds.get(playerUuid);
        GameTeam team = teamId == null ? null : this.teams.get(teamId);
        return team == null ? fallback : team.label();
    }

    public TeamRole role(UUID playerUuid) {
        String teamId = this.playerTeamIds.get(playerUuid);
        GameTeam team = teamId == null ? null : this.teams.get(teamId);
        TeamMembership membership = team == null ? null : team.membership(playerUuid);
        return membership == null ? TeamRole.MEMBER : membership.role();
    }

    public List<TeamSnapshot> snapshots() {
        return this.teams.values().stream()
            .filter(team -> !team.isEmpty())
            .map(GameTeam::snapshot)
            .toList();
    }

    public List<TeamSnapshot> snapshots(Collection<String> teamIds) {
        Set<String> ids = teamIds.stream().map(GameTeam::sanitizeId).collect(java.util.stream.Collectors.toSet());
        return this.teams.values().stream()
            .filter(team -> ids.contains(team.id()))
            .filter(team -> !team.isEmpty())
            .map(GameTeam::snapshot)
            .toList();
    }

    public List<TeamMembership> membershipsWithRole(TeamRole role) {
        TeamRole resolvedRole = role == null ? TeamRole.MEMBER : role;
        return this.teams.values().stream()
            .flatMap(team -> team.members().stream())
            .filter(member -> member.role() == resolvedRole)
            .toList();
    }

    public List<UUID> playerUuidsWithRole(TeamRole role) {
        return this.membershipsWithRole(role).stream().map(TeamMembership::playerUuid).toList();
    }

    public int countTeamsWithAny(Collection<UUID> playerUuids) {
        return this.teamLabelsWithAny(playerUuids).size();
    }

    public String singleTeamLabel(Collection<UUID> playerUuids) {
        Set<String> labels = this.teamLabelsWithAny(playerUuids);
        return labels.size() == 1 ? labels.iterator().next() : "";
    }

    private Set<String> teamLabelsWithAny(Collection<UUID> playerUuids) {
        if (playerUuids == null || playerUuids.isEmpty()) {
            return Set.of();
        }
        return playerUuids.stream()
            .map(this.playerTeamIds::get)
            .filter(java.util.Objects::nonNull)
            .map(this.teams::get)
            .filter(java.util.Objects::nonNull)
            .map(GameTeam::label)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
