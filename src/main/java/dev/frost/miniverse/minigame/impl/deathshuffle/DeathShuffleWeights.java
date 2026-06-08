package dev.frost.miniverse.minigame.impl.deathshuffle;

import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjective;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.random.Random;

public final class DeathShuffleWeights {
    private DeathShuffleWeights() {
    }

    public static DeathObjective selectRandomObjective(MinecraftServer server, DeathShuffleSettings settings, Random random) {
        java.util.Map<Identifier, DeathObjective> allObjectives = dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveManager.getAll(server);
        List<DeathObjective> pool = new ArrayList<>();

        if (settings.activeObjectivePool() != null && !settings.activeObjectivePool().isEmpty()) {
            for (Identifier id : settings.activeObjectivePool()) {
                DeathObjective objective = dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveManager.get(server, id);
                if (objective != null) {
                    pool.add(objective);
                }
            }
        } else {
            // Default: add all
            pool.addAll(allObjectives.values());
        }

        if (pool.isEmpty()) {
            return null;
        }

        return pool.get(random.nextInt(pool.size()));
    }
}
