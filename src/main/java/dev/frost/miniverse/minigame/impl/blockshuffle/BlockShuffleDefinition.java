package dev.frost.miniverse.minigame.impl.blockshuffle;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Properties;

public final class BlockShuffleDefinition implements MinigameDefinition {
    public static final String ID = "block_shuffle";
    public static final String DISPLAY_NAME = "Block Shuffle";

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
            "Race to find and stand on your assigned block before the timer runs out!",
            "🧊",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        BlockShuffleSettings.fromNbt(settings).writeTo(properties);
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
    }

    @Override
    public void registerEvents() {
        BlockShuffleSessionBootstrap.register();
    }
}
