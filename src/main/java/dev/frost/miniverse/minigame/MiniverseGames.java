package dev.frost.miniverse.minigame;

import dev.frost.miniverse.minigame.core.MinigameRegistry;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntDefinition;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapDefinition;
import dev.frost.miniverse.minigame.impl.infection.InfectionDefinition;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntDefinition;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintDefinition;
import dev.frost.miniverse.minigame.impl.speedrun.SpeedrunDefinition;

public final class MiniverseGames {
    private static boolean registered;

    private MiniverseGames() {
    }

    public static synchronized void registerAll() {
        if (registered) {
            return;
        }

        MinigameRegistry.register(new ManhuntDefinition());
        MinigameRegistry.register(new SpeedrunDefinition());
        MinigameRegistry.register(new BountyHuntDefinition());
        MinigameRegistry.register(new ResourceSprintDefinition());
        MinigameRegistry.register(new DeathSwapDefinition());
        MinigameRegistry.register(new InfectionDefinition());
        registered = true;
    }
}
