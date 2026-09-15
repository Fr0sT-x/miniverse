package dev.frost.miniverse.minigame.impl.horde;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Properties;

public final class HordeSurvivalDefinition implements MinigameDefinition {
    public static final String ID = "horde_survival";
    public static final String DISPLAY_NAME = "Horde Survival";

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
            "Survive escalating mob hordes, boot and fuel military transmitter pods, and reach extraction.",
            "🛡",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        HordeSurvivalSettings.fromNbt(settings).writeTo(properties);
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
    }

    @Override
    public void registerEvents() {
        HordeSurvivalGameEvents.register();
    }
}
