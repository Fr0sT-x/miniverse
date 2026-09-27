package dev.frost.miniverse.minigame.impl.bridge;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.map.MapGamemodeRegistry;
import dev.frost.miniverse.map.MapGamemodeType;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.MarkerType;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.map.region.TriggerType;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class BridgeDefinition implements MinigameDefinition {
    public static final String ID = "bridge";
    public static final String DISPLAY_NAME = "The Bridge";

    public static final String RED_TEAM_SPAWN = "red_team_spawn";
    public static final String BLUE_TEAM_SPAWN = "blue_team_spawn";
    public static final String RED_TEAM_GOAL = "red_team_goal";
    public static final String BLUE_TEAM_GOAL = "blue_team_goal";
    public static final String VOID_LEVEL = "void_level";
    public static final String HEIGHT_LIMIT = "height_limit";

    public static final MapEditorExtension EXTENSION = new MapEditorExtension(
        ID,
        DISPLAY_NAME,
        List.of(
            new MarkerDefinition(
                RED_TEAM_SPAWN, "Red Team Spawn", MarkerType.POINT, "redTeamSpawns", 1, Integer.MAX_VALUE, null,
                "Spawn location for Red Team players.\n" +
                "• Required: At least 1 point (1 or more).\n" +
                "• Purpose: Red Team members spawn here at match start, round resets, and after respawning.\n" +
                "• Placement: Place inside the Red base spawn cage facing outward toward the central bridge walkway."
            ),
            new MarkerDefinition(
                BLUE_TEAM_SPAWN, "Blue Team Spawn", MarkerType.POINT, "blueTeamSpawns", 1, Integer.MAX_VALUE, null,
                "Spawn location for Blue Team players.\n" +
                "• Required: At least 1 point (1 or more).\n" +
                "• Purpose: Blue Team members spawn here at match start, round resets, and after respawning.\n" +
                "• Placement: Place inside the Blue base spawn cage facing outward toward the central bridge walkway."
            ),
            new MarkerDefinition(
                RED_TEAM_GOAL, "Red Team Goal", MarkerType.REGION, "redTeamGoal", 1, 1, List.of(TriggerType.PLAYER_ENTER),
                "The goal pit or portal inside the Red Team base.\n" +
                "• Required: Exactly 1 region (1/1).\n" +
                "• Purpose: Blue Team players leap into this region to score a goal against the Red Team.\n" +
                "• Placement: Define the 3D volume of the goal hole/pit in front of the Red spawn cage."
            ),
            new MarkerDefinition(
                BLUE_TEAM_GOAL, "Blue Team Goal", MarkerType.REGION, "blueTeamGoal", 1, 1, List.of(TriggerType.PLAYER_ENTER),
                "The goal pit or portal inside the Blue Team base.\n" +
                "• Required: Exactly 1 region (1/1).\n" +
                "• Purpose: Red Team players leap into this region to score a goal against the Blue Team.\n" +
                "• Placement: Define the 3D volume of the goal hole/pit in front of the Blue spawn cage."
            ),
            new MarkerDefinition(
                VOID_LEVEL, "Void Death Level Reference", MarkerType.POINT, "voidLevel", 1, 1, null,
                "Height reference point defining the instant-kill void threshold.\n" +
                "• Required: Exactly 1 point (1/1).\n" +
                "• Purpose: Any player whose Y coordinate falls below this marker's level is instantly eliminated and respawned.\n" +
                "• Placement: Place at or below the lowest bridge walkway blocks where falling players should trigger a void reset."
            ),
            new MarkerDefinition(
                HEIGHT_LIMIT, "Build Height Limit Reference", MarkerType.POINT, "heightLimit", 0, 1, null,
                "Height reference point defining the maximum build height limit.\n" +
                "• Optional: Up to 1 point (0 to 1).\n" +
                "• Purpose: Prevents players from building excessively high towers or sky bridges above the playable bridge.\n" +
                "• Placement: Place at the desired highest allowable block placement level above the bridge walkway."
            ),
            new MarkerDefinition(
                "custom_region", "Custom Region", MarkerType.REGION, "customRegions", 0, Integer.MAX_VALUE, null,
                "Custom bounding region for map-specific building or breaking rules.\n" +
                "• Optional: Up to unlimited regions.\n" +
                "• Purpose: Allows server admins to enforce restrictions like BUILD_DENIED or BREAK_DENIED on base structures.\n" +
                "• Placement: Enclose base structures, portals, or spawn cages where block modification should be restricted."
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
            "Bridge to the enemy side and enter their goal to score points.",
            "⚔",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        BridgeSettings parsed = BridgeSettings.fromNbt(settings);
        parsed.writeTo(properties);
        if (!parsed.mapId().isBlank()) {
            dev.frost.miniverse.map.MapStore.readGamemodeConfig(parsed.mapId(), ID)
                .ifPresent(config -> properties.setProperty("bridge.mapConfig", config.toString()));
        }
    }

    @Override
    public void writeLaunchProperties(NbtCompound settings, Map<String, String> properties) {
        BridgeSettings parsed = BridgeSettings.fromNbt(settings);
        if (!parsed.mapId().isBlank()) {
            properties.put("miniverse.bridge.mapId", parsed.mapId());
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
    }

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, BridgeMapConfig::validateEditor));
        MapEditorExtensionRegistry.register(EXTENSION);
        BridgeGameEvents.register();
    }
}
