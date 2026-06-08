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
            new MarkerDefinition(RED_TEAM_SPAWN, "Red Team Spawn", MarkerType.POINT, "redTeamSpawns", 1, Integer.MAX_VALUE, null, "Players on the Red Team spawn here."),
            new MarkerDefinition(BLUE_TEAM_SPAWN, "Blue Team Spawn", MarkerType.POINT, "blueTeamSpawns", 1, Integer.MAX_VALUE, null, "Players on the Blue Team spawn here."),
            new MarkerDefinition(RED_TEAM_GOAL, "Red Team Goal", MarkerType.REGION, "redTeamGoal", 1, 1, List.of(TriggerType.PLAYER_ENTER), "Enemy team scores by entering this region."),
            new MarkerDefinition(BLUE_TEAM_GOAL, "Blue Team Goal", MarkerType.REGION, "blueTeamGoal", 1, 1, List.of(TriggerType.PLAYER_ENTER), "Enemy team scores by entering this region."),
            new MarkerDefinition(VOID_LEVEL, "Void Death Level Reference", MarkerType.POINT, "voidLevel", 1, 1, null, "Select a block. The insta-death void level will be set an offset below this point."),
            new MarkerDefinition(HEIGHT_LIMIT, "Build Height Limit Reference", MarkerType.POINT, "heightLimit", 0, 1, null, "Select a block. The build height limit will be set to an offset above this point."),
            new MarkerDefinition("custom_region", "Custom Region", MarkerType.REGION, "customRegions", 0, Integer.MAX_VALUE, null, "A generic region. Use this to apply custom restrictions like BUILD_DENIED anywhere on the map.")
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
