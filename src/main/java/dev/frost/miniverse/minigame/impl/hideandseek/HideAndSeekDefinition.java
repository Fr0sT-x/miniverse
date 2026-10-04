package dev.frost.miniverse.minigame.impl.hideandseek;

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

public final class HideAndSeekDefinition implements MinigameDefinition {
    public static final String ID = "hideandseek";
    public static final String DISPLAY_NAME = "Hide and Seek";

    public static final String HIDER_SPAWNS = "hider_spawns";
    public static final String SEEKER_SPAWNS = "seeker_spawns";
    public static final String WAITING_LOBBY = "waiting_lobby";
    public static final String BLOCK_POOL = "block_pool";

    public static final MapEditorExtension EXTENSION = new MapEditorExtension(
        ID,
        DISPLAY_NAME,
        List.of(
            new MarkerDefinition(
                HIDER_SPAWNS, "Hider Spawns", MarkerType.POINT, "hiderSpawns", 1, 32, null,
                "Spawn points where Hiders start and begin finding hiding spots.\n" +
                "• Required: At least 1 point in the main arena area.\n" +
                "• Purpose: Disperses hiders across the map during the grace period."
            ),
            new MarkerDefinition(
                SEEKER_SPAWNS, "Seeker Holding Spawns", MarkerType.POINT, "seekerSpawns", 1, 16, null,
                "Holding cage/room where Seekers are contained during the grace period.\n" +
                "• Required: At least 1 point in an enclosed room away from the hiders.\n" +
                "• Purpose: Contains seekers with blindness/slowness until released."
            ),
            new MarkerDefinition(
                WAITING_LOBBY, "Waiting Lobby", MarkerType.POINT, "waitingLobby", 0, 1, null,
                "Optional pre-game lobby spawn before match starts.\n" +
                "• Purpose: Safe platform where players wait before roles are assigned."
            ),
            new MarkerDefinition(
                BLOCK_POOL, "Disguise Block Pool", MarkerType.POINT, "disguiseBlocks", 0, 100, null,
                "Blocks available for Hiders to disguise as on this map.\n" +
                "• Quick Setup: Put desired blocks in your hotbar (slots 1-9) and click [⚡ Scan Hotbar]!\n" +
                "• Purpose: Controls which blocks hiders can choose to blend into the map."
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
            "Disguise as solid blocks, blend into the map, and evade the Seekers!",
            "📦",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        String mapId = settings.contains("mapId") ? settings.getString("mapId").trim() : "";
        if (!mapId.isBlank()) {
            properties.setProperty("hideandseek.mapId", mapId);
            dev.frost.miniverse.map.MapStore.readGamemodeConfig(mapId, ID)
                .ifPresent(config -> properties.setProperty("hideandseek.mapConfig", config.toString()));
        }
        if (settings.contains("disguiseBlocks")) {
            properties.setProperty("hideandseek.disguiseBlocks", settings.getString("disguiseBlocks"));
        }
        HideAndSeekSettings.fromNbt(settings).writeTo(properties);
    }

    @Override
    public void writeLaunchProperties(NbtCompound settings, Map<String, String> properties) {
        if (settings.contains("mapId")) {
            properties.put("miniverse.hideandseek.mapId", settings.getString("mapId"));
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
    }

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, (descriptor, config) -> HideAndSeekMapConfig.fromGamemodeJson(config).validate()));
        MapEditorExtensionRegistry.register(EXTENSION);
        HideAndSeekSessionBootstrap.register();
    }
}
