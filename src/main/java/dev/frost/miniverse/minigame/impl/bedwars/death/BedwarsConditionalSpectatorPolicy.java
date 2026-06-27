package dev.frost.miniverse.minigame.impl.bedwars.death;

import dev.frost.miniverse.minigame.core.SessionRoster;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.FixedCameraSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.FreeFlySpectatorPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import dev.frost.miniverse.minigame.impl.bedwars.BedTeamState;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;

public final class BedwarsConditionalSpectatorPolicy implements DeathSpectatorPolicy {
    private final Map<String, BedTeamState> bedTeamStates;
    private final SpectatorService spectatorService;
    private final SessionRoster roster;

    public BedwarsConditionalSpectatorPolicy(Map<String, BedTeamState> bedTeamStates, SpectatorService spectatorService, SessionRoster roster) {
        this.bedTeamStates = bedTeamStates;
        this.spectatorService = spectatorService;
        this.roster = roster;
    }

    @Override
    public void apply(ServerPlayerEntity victim, DeathContext context) {
        String teamId = context.victimTeamId();
        boolean bedAlive = teamId != null
            && bedTeamStates.containsKey(teamId)
            && bedTeamStates.get(teamId).isBedAlive();

        if (bedAlive) {
            new FixedCameraSpectatorPolicy(
                spectatorService,
                SpectatorPolicies.lockedSwitching(),
                SpectatorTargetProviders.roster(),
                NoTargetPolicy.FREEZE
            ).apply(victim, context);
        } else {
            new FreeFlySpectatorPolicy(spectatorService).apply(victim, context);
        }
    }

    @Override public boolean requiresFixedCamera() { return false; }
    @Override public NoTargetPolicy noTargetPolicy() { return NoTargetPolicy.FREE_FLY; }
}
