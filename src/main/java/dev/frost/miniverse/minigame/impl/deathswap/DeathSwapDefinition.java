package dev.frost.miniverse.minigame.impl.deathswap;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapGameEvents;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Properties;

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
    public SessionTopology topology() {
        return SessionTopology.SHARED_WORLD;
    }

    @Override
    public MinigameMetadata metadata() {
        return MinigameMetadata.custom(
            this.id(),
            this.displayName(),
            "Survive swaps and outlast every other player or team.",
            "↔",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        DeathSwapSettings.fromNbt(settings).writeTo(properties);
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
    }

    @Override
    public void registerEvents() {
        DeathSwapGameEvents.register();
    }
}


