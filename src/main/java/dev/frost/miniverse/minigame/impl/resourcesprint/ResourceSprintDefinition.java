package dev.frost.miniverse.minigame.impl.resourcesprint;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintGameEvents;
import net.minecraft.server.command.ServerCommandSource;

public final class ResourceSprintDefinition implements MinigameDefinition {
    public static final String ID = "resource_sprint";
    public static final String DISPLAY_NAME = "Resource Sprint";

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
        // Resource Sprint currently relies on the session setup UI and the minigame lifecycle.
    }

    @Override
    public void registerEvents() {
        ResourceSprintGameEvents.register();
    }
}


