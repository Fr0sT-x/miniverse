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
            if (context.location() != null && context.dimension() != null) {
                net.minecraft.server.world.ServerWorld world = victim.getServer().getWorld(context.dimension());
                if (world != null) {
                    double safeY = Math.max(context.location().y, 100.0);
                    victim.teleport(world, context.location().x, safeY, context.location().z, java.util.Set.of(), context.yawAtDeath(), context.pitchAtDeath());
                }
            }
            this.spectatorService.startSpectating(
                victim,
                SpectatorPolicies.unrestricted(),
                SpectatorTargetProviders.none(),
                dev.frost.miniverse.minigame.core.spectator.SpectatorMode.STANDARD,
                null,
                null,
                null,
                NoTargetPolicy.FREEZE
            );
        } else {
            new FreeFlySpectatorPolicy(spectatorService).apply(victim, context);
        }
    }

    @Override public boolean requiresFixedCamera() { return false; }
    @Override public NoTargetPolicy noTargetPolicy() { return NoTargetPolicy.FREE_FLY; }
}
