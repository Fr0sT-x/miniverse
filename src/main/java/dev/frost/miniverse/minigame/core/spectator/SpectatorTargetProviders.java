package dev.frost.miniverse.minigame.core.spectator;

import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class SpectatorTargetProviders {
    private SpectatorTargetProviders() {
    }

    public static SpectatorTargetProvider none() {
        return context -> List.of();
    }

    public static SpectatorTargetProvider roster() {
        return context -> new ArrayList<>(context.liveParticipants());
    }

    public static SpectatorTargetProvider onlinePlayers() {
        return context -> new ArrayList<>(context.server().getPlayerManager().getPlayerList());
    }

    public static SpectatorTargetProvider fixed(UUID targetId) {
        return context -> {
            Entity target = SpectatorUtils.findEntity(context.server(), targetId);
            return target == null ? List.of() : List.of(target);
        };
    }

    public static SpectatorTargetProvider killer(UUID killerId) {
        return fixed(killerId);
    }

    public static SpectatorTargetProvider filtered(SpectatorTargetProvider base, Predicate<Entity> predicate) {
        return context -> {
            List<Entity> targets = base == null ? List.of() : base.findTargets(context);
            if (predicate == null || targets.isEmpty()) {
                return targets;
            }
            List<Entity> filtered = new ArrayList<>();
            for (Entity target : targets) {
                if (predicate.test(target)) {
                    filtered.add(target);
                }
            }
            return filtered;
        };
    }
}

