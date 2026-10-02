package dev.frost.miniverse.minigame.impl.ctf;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.map.MapGamemodeRegistry;
import dev.frost.miniverse.map.MapGamemodeType;
import dev.frost.miniverse.map.MapStore;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.MarkerGrouping;
import dev.frost.miniverse.map.editor.MarkerType;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class CaptureTheFlagDefinition implements MinigameDefinition {
    public static final String ID = "ctf";
    public static final String DISPLAY_NAME = "Capture The Flag";

    public static final MapEditorExtension EXTENSION = new MapEditorExtension(
        ID, DISPLAY_NAME,
        List.of(
            new MarkerDefinition(
                "team_config", "Team", MarkerType.POINT, "teamConfigs", 2, 8, null, null,
                "Defines a participating team base and identity.\n" +
                "• Required: 2 to 8 teams (e.g. Red, Blue, Green, Yellow).\n" +
                "• Purpose: Central parent marker for each team. All team spawns, flags, dropoff zones, and shop NPCs are grouped under a Team.\n" +
                "• Placement: Place near the center of the team's base island. Open Properties to set team name and banner color."
            ),
            new MarkerDefinition(
                "team_spawn", "Team Spawn", MarkerType.POINT, "teamSpawns", 1, Integer.MAX_VALUE, null,
                MarkerGrouping.logical("team_config", "teamId"),
                "Spawn location for players assigned to this team.\n" +
                "• Required: At least 1 per team (1 to 4 recommended).\n" +
                "• Purpose: Team members spawn here at match start and respawn here after dying while their team has lives.\n" +
                "• Placement: Place on safe, solid blocks at the team's base facing toward the center."
            ),
            new MarkerDefinition(
                "team_flag", "Team Flag", MarkerType.POINT, "teamFlags", 1, Integer.MAX_VALUE, null,
                MarkerGrouping.logical("team_config", "teamId"),
                "The team's banner flag location that players must defend.\n" +
                "• Required: Exactly 1 per team.\n" +
                "• Purpose: Holds the team's banner on a pedestal. Enemies steal it from here, and dropped flags return here.\n" +
                "• Placement: Place on a raised pedestal, fence post, or prominent altar in the team base."
            ),
            new MarkerDefinition(
                "team_dropoff", "Flag Dropoff", MarkerType.POINT, "teamDropoffs", 0, Integer.MAX_VALUE, null,
                MarkerGrouping.logical("team_config", "teamId"),
                "Pad where stolen enemy flags are captured.\n" +
                "• Optional: 0 or 1 per team. If omitted, the team flag pedestal serves as the dropoff point.\n" +
                "• Purpose: Team carriers step here with an enemy banner to score a capture for their team.\n" +
                "• Placement: Place on a gold or beacon capture pad near the team flag post."
            ),
            new MarkerDefinition(
                "shop_npc", "Item Shop NPC", MarkerType.POINT, "shopNpcs", 0, Integer.MAX_VALUE, null,
                MarkerGrouping.logical("team_config", "teamId"),
                "Station where players interact to open the Item and Upgrade Shop.\n" +
                "• Optional: 0 or more per team.\n" +
                "• Purpose: Spawns an NPC where players spend combat coins and gems on equipment, blocks, potions, and upgrades.\n" +
                "• Placement: Place on a prominent block near the team spawn with clear player foot space."
            ),
            new MarkerDefinition(
                "powerup_location", "Powerup Spawner", MarkerType.POINT, "powerupLocations", 0, 16, null, null,
                "Location where neutral floating powerups spawn during the match.\n" +
                "• Optional: 0 to 16 generators.\n" +
                "• Purpose: Spawns periodic rotating powerups (Strength, Speed, Absorption, Heal, Bonus Coins, Bridge Snowballs).\n" +
                "• Placement: Place hovering 1 block above ground in central contested zones and along side bridges."
            ),
            new MarkerDefinition(
                "void_level", "Void Death Level", MarkerType.POINT, "voidLevel", 1, 1, null, null,
                "Void cutoff Y level reference.\n" +
                "• Required: Exactly 1 marker per map.\n" +
                "• Purpose: Determines the Y coordinate threshold at which falling players are killed by the void and returned.\n" +
                "• Placement: Place below the lowest island level in the void."
            ),
            new MarkerDefinition(
                "height_limit", "Build Height Limit Reference", MarkerType.POINT, "heightLimit", 0, 1, null, null,
                "Height reference point defining the maximum build height limit.\n" +
                "• Optional: Up to 1 point (0 to 1).\n" +
                "• Purpose: Prevents players from building excessively high towers or sky bridges.\n" +
                "• Placement: Place at the desired highest allowable block placement level."
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
            "Capture enemy banners and defend your base.",
            "🚩",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        CaptureTheFlagSettings parsed = CaptureTheFlagSettings.fromNbt(settings);
        parsed.writeTo(properties);
        if (!parsed.mapId().isBlank()) {
            MapStore.readGamemodeConfig(parsed.mapId(), ID)
                .ifPresent(config -> properties.setProperty("ctf.mapConfig", config.toString()));
        }
    }

    @Override
    public void writeLaunchProperties(NbtCompound settings, Map<String, String> properties) {
        CaptureTheFlagSettings parsed = CaptureTheFlagSettings.fromNbt(settings);
        if (!parsed.mapId().isBlank()) {
            properties.put("miniverse.ctf.mapId", parsed.mapId());
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
    }

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, CaptureTheFlagMapConfig::validateEditor));
        MapEditorExtensionRegistry.register(EXTENSION);
        CaptureTheFlagSessionBootstrap.register();
    }
}
