package dev.frost.miniverse.minigame.impl.resourcesprint;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintGameEvents;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Properties;

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
    public SessionTopology topology() {
        return SessionTopology.SHARED_WORLD;
    }

    @Override
    public MinigameMetadata metadata() {
        return MinigameMetadata.custom(
            this.id(),
            this.displayName(),
            "Race through a visible objective chain to finish first.",
            "⛏",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        ResourceSprintSettings.fromNbt(settings).writeTo(properties);
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


