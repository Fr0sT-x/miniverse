package dev.frost.miniverse.minigame.impl.duels;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.map.MapGamemodeRegistry;
import dev.frost.miniverse.map.MapGamemodeType;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.MarkerType;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class DuelsDefinition implements MinigameDefinition {
    public static final String ID = "duels";
    public static final String DISPLAY_NAME = "Duels";

    public static final String ARENA = "arena";
    public static final String PLAYER_1_SPAWN = "player_1_spawn";
    public static final String PLAYER_2_SPAWN = "player_2_spawn";
    public static final String SPECTATOR_SPAWN = "spectator_spawn";

    public static final MapEditorExtension EXTENSION = new MapEditorExtension(
        ID,
        DISPLAY_NAME,
        List.of(
            new MarkerDefinition(ARENA, "Duel Arena", MarkerType.REGION, "arenas", 1, Integer.MAX_VALUE, null, "A region defining a single 1v1 arena. Set properties 'id' for unique ID, and 'tags' as a JSON array (e.g. [\"bridge\", \"boxing\"])."),
            new MarkerDefinition(PLAYER_1_SPAWN, "Player 1 Spawn", MarkerType.POINT, "player1Spawns", 1, Integer.MAX_VALUE, null, "Spawn point for Player 1. Place exactly one inside each Duel Arena."),
            new MarkerDefinition(PLAYER_2_SPAWN, "Player 2 Spawn", MarkerType.POINT, "player2Spawns", 1, Integer.MAX_VALUE, null, "Spawn point for Player 2. Place exactly one inside each Duel Arena."),
            new MarkerDefinition(SPECTATOR_SPAWN, "Spectator Spawn", MarkerType.POINT, "spectatorSpawns", 1, Integer.MAX_VALUE, null, "Spawn point for Spectators. Place exactly one inside each Duel Arena.")
        ),
        List.of(DuelsMapConfig::validateArenas)
    );

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
            "A 1v1 battle testing your PvP skills across multiple kits and arenas.",
            "⚔",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settingsNbt, Properties properties) {
        // Will implement config parsing here in the future
    }

    @Override
    public void writeLaunchProperties(NbtCompound settingsNbt, Map<String, String> properties) {
        // Will implement config mapping here in the future
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Future duels specific commands
    }

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, DuelsMapConfig::validateEditor));
        MapEditorExtensionRegistry.register(EXTENSION);
    }
}
