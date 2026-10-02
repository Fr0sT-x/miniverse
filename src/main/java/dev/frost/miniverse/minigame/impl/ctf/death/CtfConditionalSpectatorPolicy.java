package dev.frost.miniverse.minigame.impl.ctf.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.FreeFlySpectatorPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import dev.frost.miniverse.minigame.core.visibility.TeamGlowVisibility;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagMinigame;
import dev.frost.miniverse.minigame.impl.ctf.flag.CtfFlagInstance;
import dev.frost.miniverse.minigame.impl.ctf.flag.CtfFlagState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Set;

public final class CtfConditionalSpectatorPolicy implements DeathSpectatorPolicy {
    private final CaptureTheFlagMinigame minigame;
    private final SpectatorService spectatorService;

    public CtfConditionalSpectatorPolicy(CaptureTheFlagMinigame minigame, SpectatorService spectatorService) {
        this.minigame = minigame;
        this.spectatorService = spectatorService;
    }

    @Override
    public void apply(ServerPlayerEntity victim, DeathContext context) {
        String teamId = context.victimTeamId();
        CtfFlagInstance flag = teamId != null ? this.minigame.getFlagManager().getFlag(teamId) : null;
        boolean canRespawn = !this.minigame.getSettings().eliminationMode()
            || (flag != null && flag.state() != CtfFlagState.CAPTURED);

        if (canRespawn) {
            if (context.location() != null && context.dimension() != null) {
                ServerWorld world = victim.getServer().getWorld(context.dimension());
                if (world != null) {
                    double safeY = Math.max(context.location().y, 100.0);
                    victim.teleport(world, context.location().x, safeY, context.location().z, Set.of(), context.yawAtDeath(), context.pitchAtDeath());
                }
            }
            this.spectatorService.startSpectating(
                victim,
                SpectatorPolicies.unrestricted(),
                SpectatorTargetProviders.none(),
                SpectatorMode.STANDARD,
                null,
                null,
                null,
                NoTargetPolicy.FREEZE
            );
        } else {
            new FreeFlySpectatorPolicy(spectatorService).apply(victim, context);
        }
        TeamGlowVisibility.resyncFor(victim);
    }

    @Override public boolean requiresFixedCamera() { return false; }
    @Override public NoTargetPolicy noTargetPolicy() { return NoTargetPolicy.FREE_FLY; }
}
