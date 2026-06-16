package dev.frost.miniverse.minigame.core.death.strategy.impl;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.team.TeamManager;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

public class TeamSpawnStrategy implements RespawnStrategy {
    private final TeamManager teamManager;
    private final Function<String, RespawnLocation> spawnProvider;
    private final RespawnStrategy fallback;

    /**
     * @param teamManager   the team manager
     * @param spawnProvider maps a team ID to its spawn location,
     *                      or returns null if no spawn is configured for that team
     * @param fallback      the fallback strategy
     */
    public TeamSpawnStrategy(TeamManager teamManager, Function<String, RespawnLocation> spawnProvider, RespawnStrategy fallback) {
        this.teamManager = Objects.requireNonNull(teamManager, "teamManager cannot be null");
        this.spawnProvider = Objects.requireNonNull(spawnProvider, "spawnProvider cannot be null");
        this.fallback = Objects.requireNonNull(fallback, "fallback cannot be null");
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession spectatorSession) {
        String teamId = this.teamManager.teamId(context.victimId());
        
        if (teamId == null) {
            return this.fallback.resolve(context, spectatorSession);
        }

        RespawnLocation location = this.spawnProvider.apply(teamId);
        if (location != null) {
            return location;
        }

        return this.fallback.resolve(context, spectatorSession);
    }
}
