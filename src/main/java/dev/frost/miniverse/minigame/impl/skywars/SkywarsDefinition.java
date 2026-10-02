package dev.frost.miniverse.minigame.impl.skywars;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.map.MapGamemodeRegistry;
import dev.frost.miniverse.map.MapGamemodeType;
import dev.frost.miniverse.map.MapValidationResult;
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

public final class SkywarsDefinition implements MinigameDefinition {
    public static final String ID = "skywars";
    public static final String DISPLAY_NAME = "Skywars";

    public static final String SPAWNS = "spawns";
    public static final String ISLAND_CHESTS = "island_chests";
    public static final String MID_CHESTS = "mid_chests";
    public static final String WAITING_LOBBY = "waiting_lobby";
    public static final String VOID_LEVEL = "void_level";

    public static final MapEditorExtension EXTENSION = new MapEditorExtension(
        ID,
        DISPLAY_NAME,
        List.of(
            new MarkerDefinition(
                SPAWNS, "Island Spawns", MarkerType.POINT, "spawns", 2, 24, null,
                "Player spawn points on individual starter islands.\n" +
                "• Required: At least 2 points (one per island).\n" +
                "• Purpose: Players start here, encased in glass cages before the countdown ends.\n" +
                "• Placement: Place in the center of each starter island."
            ),
            new MarkerDefinition(
                ISLAND_CHESTS, "Island Chests", MarkerType.POINT, "islandChests", 0, Integer.MAX_VALUE, null,
                "Chests on starter islands containing basic tier loot.\n" +
                "• Required: At least 1 chest per island recommended.\n" +
                "• Purpose: Initial loot for players.\n" +
                "• Placement: Place on starter islands, or run /miniverse_map_scan_chests to auto-detect."
            ),
            new MarkerDefinition(
                MID_CHESTS, "Mid / Feast Chests", MarkerType.POINT, "midChests", 0, Integer.MAX_VALUE, null,
                "Center island feast chests containing high-tier loot.\n" +
                "• Purpose: High-tier contested loot at the center of the map.\n" +
                "• Placement: Place on center island, or run /miniverse_map_scan_chests to auto-detect."
            ),
            new MarkerDefinition(
                WAITING_LOBBY, "Waiting Lobby", MarkerType.POINT, "waitingLobby", 0, 1, null,
                "Optional pre-game waiting spawn before match starts.\n" +
                "• Purpose: Safe waiting area before players are placed in cages.\n" +
                "• Placement: Place on a spectator/lobby platform away from islands."
            ),
            new MarkerDefinition(
                VOID_LEVEL, "Void Level", MarkerType.POINT, "voidLevel", 0, 1, null,
                "Y-level at or below which players are eliminated by the void.\n" +
                "• Purpose: Determines the elimination boundary height for falling players.\n" +
                "• Placement: Place at the desired void cutoff Y level (e.g. Y=0)."
            )
        ),
        List.of()
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
            "Battle on floating islands, loot chests, bridge to mid, and be the last player standing.",
            "⚔",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        String mapId = settings.contains("mapId") ? settings.getString("mapId").trim() : "";
        if (!mapId.isBlank()) {
            properties.setProperty("skywars.mapId", mapId);
            dev.frost.miniverse.map.MapStore.readGamemodeConfig(mapId, ID)
                .ifPresent(config -> properties.setProperty("skywars.mapConfig", config.toString()));
        }
        SkywarsSettings.fromNbt(settings).writeTo(properties);
    }

    @Override
    public void writeLaunchProperties(NbtCompound settings, Map<String, String> properties) {
        if (settings.contains("mapId")) {
            properties.put("miniverse.skywars.mapId", settings.getString("mapId"));
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
    }

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, (descriptor, config) -> MapValidationResult.builder().build()));
        MapEditorExtensionRegistry.register(EXTENSION);
        SkywarsSessionBootstrap.register();
    }
}
