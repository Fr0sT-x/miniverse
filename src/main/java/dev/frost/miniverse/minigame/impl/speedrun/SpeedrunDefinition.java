package dev.frost.miniverse.minigame.impl.speedrun;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.command.SpeedrunCommands;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.server.command.ServerCommandSource;

public final class SpeedrunDefinition implements MinigameDefinition {
    public static final String ID = "speedrun";
    public static final String DISPLAY_NAME = "Speedrun";

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
        return SessionTopology.ISOLATED_WORLD;
    }

    @Override
    public MinigameMetadata metadata() {
        return MinigameMetadata.custom(
            this.id(),
            this.displayName(),
            "Race to finish the game fastest on identical seeds.",
            "⏱",
            this.topology()
        );
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        SpeedrunCommands.register(dispatcher);
    }

    @Override
    public void registerEvents() {
        SpeedrunGameEvents.register();
    }
}
