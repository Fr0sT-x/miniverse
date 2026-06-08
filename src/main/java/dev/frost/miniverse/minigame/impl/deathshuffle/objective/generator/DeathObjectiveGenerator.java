package dev.frost.miniverse.minigame.impl.deathshuffle.objective.generator;

import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjective;
import net.minecraft.util.Identifier;

import java.util.Map;

public interface DeathObjectiveGenerator {
    Map<Identifier, DeathObjective> generate();
}
