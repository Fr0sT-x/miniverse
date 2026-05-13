package dev.frost.miniverse.minigame.impl.deathswap;

import dev.frost.miniverse.team.TeamManager;

import java.util.Collection;
import java.util.UUID;

public final class DeathSwapManager {
    public int countAliveTeams(Collection<UUID> alivePlayers, TeamManager teams) {
        return teams.countTeamsWithAny(alivePlayers);
    }

    public String resolveWinningLabel(Collection<UUID> alivePlayers, TeamManager teams) {
        return teams.singleTeamLabel(alivePlayers);
    }
}
