package dev.frost.miniverse.minigame.core.spectator.policies;

import dev.frost.miniverse.minigame.core.spectator.SpectatorContext;
import dev.frost.miniverse.minigame.core.spectator.SpectatorPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorRestrictions;
import dev.frost.miniverse.team.TeamManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

public final class SpectatorPolicies {
    private SpectatorPolicies() {
    }

    public static SpectatorPolicy unrestricted() {
        return new PredicatePolicy(SpectatorRestrictions.freecam(), (context, target) -> true);
    }

    public static SpectatorPolicy locked() {
        return new PredicatePolicy(SpectatorRestrictions.locked(), (context, target) -> true);
    }

    public static SpectatorPolicy lockedSwitching() {
        return new PredicatePolicy(SpectatorRestrictions.lockedSwitching(), (context, target) -> true);
    }

    public static SpectatorPolicy fixedTarget(UUID targetId) {
        return new PredicatePolicy(SpectatorRestrictions.locked(), (context, target) -> target != null && targetId.equals(target.getUuid()));
    }

    public static SpectatorPolicy adminFreecam() {
        return new PredicatePolicy(new SpectatorRestrictions(true, true, false, true, true), (context, target) -> true);
    }

    public static SpectatorPolicy teamOnly(TeamManager teamManager) {
        return teamOnly(teamManager, true);
    }

    public static SpectatorPolicy teamOnly(TeamManager teamManager, boolean allowTargetSwitching) {
        SpectatorRestrictions restrictions = new SpectatorRestrictions(false, allowTargetSwitching, true, false, false);
        return new PredicatePolicy(restrictions, (context, target) -> {
            if (!(target instanceof ServerPlayerEntity targetPlayer)) {
                return false;
            }
            UUID spectatorId = context.spectatorId();
            String spectatorTeam = teamManager == null ? null : teamManager.teamId(spectatorId);
            String targetTeam = teamManager == null ? null : teamManager.teamId(targetPlayer.getUuid());
            return spectatorTeam != null && Objects.equals(spectatorTeam, targetTeam);
        });
    }

    private static final class PredicatePolicy implements SpectatorPolicy {
        private final SpectatorRestrictions restrictions;
        private final BiPredicate<SpectatorContext, Entity> predicate;

        private PredicatePolicy(SpectatorRestrictions restrictions, BiPredicate<SpectatorContext, Entity> predicate) {
            this.restrictions = restrictions == null ? SpectatorRestrictions.freecam() : restrictions;
            this.predicate = predicate == null ? (context, target) -> true : predicate;
        }

        @Override
        public SpectatorRestrictions restrictions() {
            return this.restrictions;
        }

        @Override
        public boolean isTargetAllowed(SpectatorContext context, Entity target) {
            return this.predicate.test(context, target);
        }
    }
}

