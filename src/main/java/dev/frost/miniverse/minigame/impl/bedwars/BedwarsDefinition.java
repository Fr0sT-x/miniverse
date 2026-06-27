package dev.frost.miniverse.minigame.impl.bedwars;

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

public final class BedwarsDefinition implements MinigameDefinition {
    public static final String ID = "bedwars";
    public static final String DISPLAY_NAME = "Bed Wars";

    public static final MapEditorExtension EXTENSION = new MapEditorExtension(
        ID, DISPLAY_NAME,
        List.of(
            new MarkerDefinition("team_config",      "Team",              MarkerType.POINT,  "teamConfigs",    2, 8,  null, "One per team. Name it (e.g. 'Red Team'). All team markers are associated to a team."),
            new MarkerDefinition("team_spawn",       "Team Spawn",        MarkerType.POINT,  "teamSpawns",     1, Integer.MAX_VALUE, null, "Spawn point for a team (1-4 recommended)."),
            new MarkerDefinition("team_bed",         "Team Bed",          MarkerType.POINT,  "teamBeds",       1, Integer.MAX_VALUE, null, "Bed location for a team. One per team exactly."),
            new MarkerDefinition("team_island_iron", "Island Iron Gen",   MarkerType.POINT,  "islandIronGens", 1, Integer.MAX_VALUE, null, "Iron generator on a team island."),
            new MarkerDefinition("team_island_gold", "Island Gold Gen",   MarkerType.POINT,  "islandGoldGens", 0, Integer.MAX_VALUE, null, "Gold generator on a team island."),
            new MarkerDefinition("mid_diamond_gen",  "Mid Diamond Gen",   MarkerType.POINT,  "midDiamondGens", 0, 4,  null, "Diamond generator at mid island."),
            new MarkerDefinition("mid_emerald_gen",  "Mid Emerald Gen",   MarkerType.POINT,  "midEmeraldGens", 0, 2,  null, "Emerald generator at mid island (optional)."),
            new MarkerDefinition("shop_npc",         "Item Shop NPC",     MarkerType.POINT,  "shopNpcs",       1, Integer.MAX_VALUE, null, "Opens the item shop when interacted with."),
            new MarkerDefinition("upgrade_npc",      "Upgrade NPC",       MarkerType.POINT,  "upgradeNpcs",    0, Integer.MAX_VALUE, null, "Opens the team upgrade shop."),
            new MarkerDefinition("spectator_spawn",  "Spectator Spawn",   MarkerType.POINT,  "spectatorSpawn", 1, 1,  null, "Camera start for eliminated players.")
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
            "Defend your bed and destroy others.",
            "🛏",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        BedwarsSettings parsed = BedwarsSettings.fromNbt(settings);
        parsed.writeTo(properties);
        if (!parsed.mapId().isBlank()) {
            dev.frost.miniverse.map.MapStore.readGamemodeConfig(parsed.mapId(), ID)
                .ifPresent(config -> properties.setProperty("bedwars.mapConfig", config.toString()));
        }
    }

    @Override
    public void writeLaunchProperties(NbtCompound settings, Map<String, String> properties) {
        BedwarsSettings parsed = BedwarsSettings.fromNbt(settings);
        if (!parsed.mapId().isBlank()) {
            properties.put("miniverse.bedwars.mapId", parsed.mapId());
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
    }

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, BedwarsMapConfig::validateEditor));
        MapEditorExtensionRegistry.register(EXTENSION);
        BedwarsSessionBootstrap.register();
    }
}
