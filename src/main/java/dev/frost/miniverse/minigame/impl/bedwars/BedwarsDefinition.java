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
            new MarkerDefinition(
                "team_config", "Team", MarkerType.POINT, "teamConfigs", 2, 8, null, null,
                "Defines a participating team island and team identity.\n" +
                "• Required: 2 to 8 teams (e.g. Red, Blue, Green, Yellow).\n" +
                "• Purpose: Central parent marker for each team. All team spawns, beds, generators, and shop NPCs are grouped under a Team.\n" +
                "• Placement: Place near the center of the team's home base island. Open Properties to name the team and set team color."
            ),
            new MarkerDefinition(
                "team_spawn", "Team Spawn", MarkerType.POINT, "teamSpawns", 1, Integer.MAX_VALUE, null,
                dev.frost.miniverse.map.editor.MarkerGrouping.logical("team_config", "teamId"),
                "Spawn location for players assigned to this team.\n" +
                "• Required: At least 1 per team (1 to 4 recommended).\n" +
                "• Purpose: Team members spawn here at match start and respawn here after dying while their bed is intact.\n" +
                "• Placement: Place on safe, solid blocks on the team's home island facing toward the island center."
            ),
            new MarkerDefinition(
                "team_bed", "Team Bed", MarkerType.POINT, "teamBeds", 1, Integer.MAX_VALUE, null,
                dev.frost.miniverse.map.editor.MarkerGrouping.logical("team_config", "teamId"),
                "The team's bed block location protecting player respawns.\n" +
                "• Required: Exactly 1 per team.\n" +
                "• Purpose: As long as this bed remains unbroken, team members can respawn. Once broken, members are on their final life.\n" +
                "• Placement: Select the head or foot block of the team's bed on their base island."
            ),
            new MarkerDefinition(
                "team_island_iron", "Island Iron Gen", MarkerType.POINT, "islandIronGens", 1, 64, null,
                dev.frost.miniverse.map.editor.MarkerGrouping.logical("team_config", "teamId"),
                "Resource spawner for iron ingots on the team's home island.\n" +
                "• Required: At least 1 per team (1 to 64).\n" +
                "• Purpose: Drops iron ingots periodically for team members to purchase blocks and basic equipment.\n" +
                "• Placement: Place floating 1 block above the collection hopper or drop pad on the team island."
            ),
            new MarkerDefinition(
                "team_island_gold", "Island Gold Gen", MarkerType.POINT, "islandGoldGens", 0, 64, null,
                dev.frost.miniverse.map.editor.MarkerGrouping.logical("team_config", "teamId"),
                "Resource spawner for gold ingots on the team's home island.\n" +
                "• Optional: Up to 64 per team (0 to 64).\n" +
                "• Purpose: Drops gold ingots periodically for purchasing iron armor, bows, golden apples, and higher-tier gear.\n" +
                "• Placement: Place floating 1 block above the collection pad, usually beside or overlapping the iron generator."
            ),
            new MarkerDefinition(
                "mid_diamond_gen", "Mid Diamond Gen", MarkerType.POINT, "midDiamondGens", 0, 64, null, null,
                "Neutral Diamond generator located on diamond or middle islands.\n" +
                "• Optional: Up to 64 generators (0 to 64).\n" +
                "• Purpose: Spawns diamonds used to purchase team-wide upgrades (protection, sharpness, traps, forge tiers).\n" +
                "• Placement: Place centered on dedicated neutral diamond islands between team bases."
            ),
            new MarkerDefinition(
                "mid_emerald_gen", "Mid Emerald Gen", MarkerType.POINT, "midEmeraldGens", 0, 64, null, null,
                "High-tier Emerald generator located at the center of the map.\n" +
                "• Optional: Up to 64 generators (0 to 64).\n" +
                "• Purpose: Spawns emeralds used for end-game gear (diamond armor, obsidian, pearls, bridge eggs, and punch bows).\n" +
                "• Placement: Place at the focal points of the central middle island."
            ),
            new MarkerDefinition(
                "shop_npc", "Item Shop NPC", MarkerType.POINT, "shopNpcs", 1, Integer.MAX_VALUE, null,
                dev.frost.miniverse.map.editor.MarkerGrouping.logical("team_config", "teamId"),
                "Station where players interact to open the Item Shop.\n" +
                "• Required: At least 1 per team (1 or more).\n" +
                "• Purpose: Spawns an NPC or interactable villager where players spend iron, gold, and emeralds on gear and blocks.\n" +
                "• Placement: Place on a prominent block near the team spawn with clear player access and foot space."
            ),
            new MarkerDefinition(
                "upgrade_npc", "Upgrade NPC", MarkerType.POINT, "upgradeNpcs", 0, Integer.MAX_VALUE, null,
                dev.frost.miniverse.map.editor.MarkerGrouping.logical("team_config", "teamId"),
                "Station where players interact to purchase Team Upgrades.\n" +
                "• Optional: Up to 64 per team (0 to 64).\n" +
                "• Purpose: Spawns an NPC where players spend diamonds on team buffs (armor protection, miner fatigue traps, heal pools).\n" +
                "• Placement: Place near the Item Shop NPC on the team island for convenience."
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
