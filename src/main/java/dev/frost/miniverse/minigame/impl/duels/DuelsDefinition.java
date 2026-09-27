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
            new MarkerDefinition(
                ARENA, "Duel Arena", MarkerType.REGION, "arenas", 1, Integer.MAX_VALUE, null, null,
                "Defines the 3D playable boundary of a single 1v1 duel arena.\n" +
                "• Required: At least 1 region (1 or more).\n" +
                "• Purpose: Bounds the fight zone. Multiple arenas can exist in one map to host simultaneous matches in parallel.\n" +
                "• Placement: Create a box enclosing the arena floor, walls, and ceiling. Configure supported duel types in Properties."
            ),
            new MarkerDefinition(
                PLAYER_1_SPAWN, "Player 1 Spawn", MarkerType.POINT, "player1Spawns", 1, Integer.MAX_VALUE, null,
                dev.frost.miniverse.map.editor.MarkerGrouping.spatial(ARENA),
                "Starting spawn position for the first duelist.\n" +
                "• Required: Exactly 1 per Duel Arena.\n" +
                "• Purpose: Player 1 teleports here during the countdown and starts the duel facing Player 2.\n" +
                "• Placement: Place on one side of the duel arena facing towards Player 2 Spawn across the center line."
            ),
            new MarkerDefinition(
                PLAYER_2_SPAWN, "Player 2 Spawn", MarkerType.POINT, "player2Spawns", 1, Integer.MAX_VALUE, null,
                dev.frost.miniverse.map.editor.MarkerGrouping.spatial(ARENA),
                "Starting spawn position for the second duelist.\n" +
                "• Required: Exactly 1 per Duel Arena.\n" +
                "• Purpose: Player 2 teleports here during the countdown and starts the duel facing Player 1.\n" +
                "• Placement: Place on the opposite side of the duel arena facing towards Player 1 Spawn across the center line."
            ),
            new MarkerDefinition(
                SPECTATOR_SPAWN, "Spectator Spawn", MarkerType.POINT, "spectatorSpawns", 1, Integer.MAX_VALUE, null,
                dev.frost.miniverse.map.editor.MarkerGrouping.spatial(ARENA),
                "Viewing location for spectators watching the duel.\n" +
                "• Required: At least 1 per Duel Arena (1 or more).\n" +
                "• Purpose: Eliminated players and queueing spectators are teleported here to spectate the ongoing match.\n" +
                "• Placement: Place on an elevated spectator platform, viewing balcony, or behind glass overlooking the duel arena."
            )
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
        String mapId = settingsNbt.contains("mapId") ? settingsNbt.getString("mapId").trim() : "";
        String duelType = settingsNbt.contains("duelType") ? settingsNbt.getString("duelType").trim() : "";
        String kitId = settingsNbt.contains("kitId") ? settingsNbt.getString("kitId").trim() : "";
        String rounds = settingsNbt.contains("rounds") ? settingsNbt.getString("rounds").trim() : "1";
        if (!mapId.isBlank()) {
            properties.setProperty("duels.mapId", mapId);
            dev.frost.miniverse.map.MapStore.readGamemodeConfig(mapId, ID)
                .ifPresent(config -> properties.setProperty("duels.mapConfig", config.toString()));
        }
        if (!duelType.isBlank()) {
            properties.setProperty("duels.duelType", duelType);
        }
        if (!kitId.isBlank()) {
            properties.setProperty("duels.kitId", kitId);
        }
        if (!rounds.isBlank()) {
            properties.setProperty("duels.rounds", rounds);
        }
    }

    @Override
    public void writeLaunchProperties(NbtCompound settingsNbt, Map<String, String> properties) {
        if (settingsNbt.contains("mapId")) {
            properties.put("miniverse.duels.mapId", settingsNbt.getString("mapId"));
        }
        if (settingsNbt.contains("duelType")) {
            properties.put("miniverse.duels.duelType", settingsNbt.getString("duelType"));
        }
        if (settingsNbt.contains("kitId")) {
            properties.put("miniverse.duels.kitId", settingsNbt.getString("kitId"));
        }
        if (settingsNbt.contains("rounds")) {
            properties.put("miniverse.duels.rounds", settingsNbt.getString("rounds"));
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Future duels specific commands
    }

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, DuelsMapConfig::validateEditor));
        MapEditorExtensionRegistry.register(EXTENSION);
        DuelsSessionBootstrap.register();
    }
}
