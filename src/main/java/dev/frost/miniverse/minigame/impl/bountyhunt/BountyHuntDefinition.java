package dev.frost.miniverse.minigame.impl.bountyhunt;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.command.BountyHuntCommands;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import net.minecraft.server.command.ServerCommandSource;

public final class BountyHuntDefinition implements MinigameDefinition {
    public static final String ID = "bountyhunt";
    public static final String DISPLAY_NAME = "Bounty Hunt";

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
        BountyHuntCommands.register(dispatcher);
    }

    @Override
    public void registerEvents() {
        BountyHuntGameEvents.register();
    }
}

