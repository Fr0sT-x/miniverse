package dev.frost.miniverse.minigame.impl.microparty;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.map.MapGamemodeRegistry;
import dev.frost.miniverse.map.MapGamemodeType;
import dev.frost.miniverse.map.MapStore;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.MarkerType;
import dev.frost.miniverse.map.region.TriggerType;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class MicroPartyDefinition implements MinigameDefinition {
    public static final String ID = "microparty";
    public static final String DISPLAY_NAME = "Micro Party";

    public static final String ARENA_BOUNDS = "arena_bounds";
    public static final String ARENA_CENTER = "arena_center";
    public static final String PLAYER_SPAWN = "player_spawn";
    public static final String LOBBY_SPAWN  = "lobby_spawn";
    public static final String COLOR_ZONE   = "color_zone";
    public static final String HIGH_GROUND  = "high_ground";
    public static final String TARGET_POINT = "targets";

    public static final MapEditorExtension EXTENSION = new MapEditorExtension(
        ID, DISPLAY_NAME,
        List.of(
            new MarkerDefinition(
                ARENA_BOUNDS, "Arena Bounds", MarkerType.REGION, "arenaBounds", 1, 1,
                List.of(TriggerType.PLAYER_EXIT), null,
                "Defines the 3D boundary volume of the playable arena stage.\n" +
                "• Required: Exactly 1 bounding region (1/1).\n" +
                "• Purpose: Any player knocked or falling outside this area during micro-challenges is eliminated or penalized.\n" +
                "• Placement: Select two opposite corners to form a bounding box enclosing the arena platform, with headroom for jumping and depth below the floor."
            ),
            new MarkerDefinition(
                ARENA_CENTER, "Arena Center", MarkerType.POINT, "arenaCenter", 0, 1,
                null, null,
                "The central focal anchor of the arena floor.\n" +
                "• Optional: Up to 1 point (0/1). If omitted, automatically computed from the center of Arena Bounds.\n" +
                "• Purpose: Serves as the origin point for event effects, dropping anvils, center-stage challenges, and spawn radius calculations.\n" +
                "• Placement: Place on top of the center block of your main arena platform."
            ),
            new MarkerDefinition(
                PLAYER_SPAWN, "Player Spawn", MarkerType.POINT, "playerSpawns", 2, 24,
                null, null,
                "Starting spawn positions for participants on the arena stage.\n" +
                "• Required: 2 to 24 points (minimum 2).\n" +
                "• Purpose: Players teleport to these locations at the start of each round and after resets.\n" +
                "• Placement: Distribute evenly across the arena floor facing towards the center. Ensure at least 2 points are placed."
            ),
            new MarkerDefinition(
                LOBBY_SPAWN, "Lobby / Spectator Spawn", MarkerType.POINT, "lobbySpawns", 1, 8,
                null, null,
                "Waiting area and spectator overlook for non-active players.\n" +
                "• Required: 1 to 8 points (minimum 1).\n" +
                "• Purpose: Pre-game waiting players and eliminated spectators are teleported here to watch the match safely.\n" +
                "• Placement: Place on an elevated spectator balcony, viewing box, or waiting lounge overlooking the arena."
            ),
            new MarkerDefinition(
                COLOR_ZONE, "Color Zone", MarkerType.REGION, "colorZones", 0, 4,
                List.of(TriggerType.PLAYER_ENTER, TriggerType.PLAYER_EXIT), null,
                "Color-coded floor pads used in 'Color Rush' challenges.\n" +
                "• Optional: Up to 4 regions (RED, BLUE, GREEN, YELLOW).\n" +
                "• Procedural: If omitted, dynamic color zones of differing sizes will automatically generate on your arena floor!\n" +
                "• Purpose: When Color Rush is called, players must stand on the announced colored floor pad before time expires.\n" +
                "• Placement: (Optional) Define custom rectangular floor pads in distinct areas of the arena, then open Properties to set the color."
            ),
            new MarkerDefinition(
                HIGH_GROUND, "High Ground", MarkerType.REGION, "highGround", 0, 8,
                List.of(TriggerType.PLAYER_ENTER), null,
                "Elevated perches and platforms used in 'Floor is Lava' challenges.\n" +
                "• Optional: Up to 8 regions.\n" +
                "• Procedural: If omitted, jumpable 1-block platforms will automatically rise from the arena floor!\n" +
                "• Purpose: When Floor is Lava triggers, players must climb onto High Ground before the floor becomes deadly.\n" +
                "• Placement: (Optional) Define custom regions on top of pillars, blocks, or platforms at least 1 block above the arena floor."
            ),
            new MarkerDefinition(
                TARGET_POINT, "Target Point", MarkerType.POINT, "targets", 0, 12,
                null, null,
                "Locations where archery targets appear in 'Shoot the Target' challenges.\n" +
                "• Optional: Up to 12 points.\n" +
                "• Procedural: If omitted, archery target blocks will automatically spawn floating in the air around the arena!\n" +
                "• Purpose: Used for quick-draw crossbow shooting micro-challenges.\n" +
                "• Placement: (Optional) Place against perimeter walls, on floating pedestals, or across the arena with clear line-of-sight for archers."
            )
        ),
        List.of(MicroPartyMapConfig::validateEditor)
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
            "A relentless barrage of 5-second micro-challenges! Think fast, react faster, and survive the chaos!",
            "⚡",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        MicroPartySettings parsed = MicroPartySettings.fromNbt(settings);
        parsed.writeTo(properties);
        if (!parsed.mapId().isBlank()) {
            MapStore.readGamemodeConfig(parsed.mapId(), ID)
                .ifPresent(config -> properties.setProperty("microparty.mapConfig", config.toString()));
        }
    }

    @Override
    public void writeLaunchProperties(NbtCompound settings, Map<String, String> properties) {
        MicroPartySettings parsed = MicroPartySettings.fromNbt(settings);
        if (!parsed.mapId().isBlank()) {
            properties.put("miniverse.microparty.mapId", parsed.mapId());
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Register primary /microparty and alias /micro_party
        for (String alias : List.of("microparty", "micro_party")) {
            dispatcher.register(
                CommandManager.literal(alias)
                    .then(CommandManager.literal("status")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (MinigameManager.getInstance().getActiveMinigame() instanceof MicroPartyMinigame mg) {
                                context.getSource().sendFeedback(() -> Text.literal("§6[Micro Party] §aActive round: " + mg.getCurrentRound() + "/" + mg.getMaxRounds()), false);
                                return 1;
                            }
                            context.getSource().sendFeedback(() -> Text.literal("Micro Party is not currently active.").formatted(Formatting.RED), false);
                            return 0;
                        })
                    )
            );
        }
    }

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, MicroPartyMapConfig::validate));
        MapEditorExtensionRegistry.register(EXTENSION);
        MicroPartySessionBootstrap.register();
    }
}
