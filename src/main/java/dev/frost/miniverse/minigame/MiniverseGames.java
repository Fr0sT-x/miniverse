package dev.frost.miniverse.minigame;

import dev.frost.miniverse.minigame.core.MinigameRegistry;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntDefinition;
import dev.frost.miniverse.minigame.impl.bridge.BridgeDefinition;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapDefinition;
import dev.frost.miniverse.minigame.impl.duels.DuelsDefinition;
import dev.frost.miniverse.minigame.impl.infection.InfectionDefinition;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntDefinition;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintDefinition;
import dev.frost.miniverse.minigame.impl.blockshuffle.BlockShuffleDefinition;
import dev.frost.miniverse.minigame.impl.deathshuffle.DeathShuffleDefinition;
import dev.frost.miniverse.minigame.impl.speedrun.SpeedrunDefinition;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsDefinition;

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
        MinigameRegistry.register(new BridgeDefinition());
        MinigameRegistry.register(new BlockShuffleDefinition());
        MinigameRegistry.register(new DeathShuffleDefinition());
        MinigameRegistry.register(new dev.frost.miniverse.minigame.impl.murdermystery.MurderMysteryDefinition());
        MinigameRegistry.register(new DuelsDefinition());
        MinigameRegistry.register(new BedwarsDefinition());
        dev.frost.miniverse.minigame.impl.murdermystery.MurderMysterySessionBootstrap.bootstrap();
        registered = true;
    }
}
