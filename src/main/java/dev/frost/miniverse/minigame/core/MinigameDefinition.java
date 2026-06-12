package dev.frost.miniverse.minigame.core;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Map;
import java.util.Properties;

/**
 * Static registration contract for a minigame module.
 */
public interface MinigameDefinition {
    String id();

    String displayName();

    SessionTopology topology();

    default MinigameMetadata metadata() {
        return new MinigameMetadata(
            this.id(),
            this.displayName(),
            "",
            "?",
            this.topology(),
            MinigameMetadata.SetupKind.GENERIC,
            true,
            java.util.List.of()
        );
    }

    default void writeSessionProperties(NbtCompound settings, Properties properties) {
    }

    default void writeLaunchProperties(NbtCompound settings, Map<String, String> properties) {
    }

    void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher);

    void registerEvents();

    default LateJoinPolicy lateJoinPolicy() {
        return new DefaultLateJoinPolicy();
    }
}
