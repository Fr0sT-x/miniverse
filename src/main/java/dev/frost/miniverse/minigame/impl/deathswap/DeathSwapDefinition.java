package dev.frost.miniverse.minigame.impl.deathswap;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapGameEvents;
import net.minecraft.server.command.ServerCommandSource;

public final class DeathSwapDefinition implements MinigameDefinition {
    public static final String ID = "deathswap";
    public static final String DISPLAY_NAME = "Death Swap";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
    }

    @Override
    public void registerEvents() {
        DeathSwapGameEvents.register();
    }
}


