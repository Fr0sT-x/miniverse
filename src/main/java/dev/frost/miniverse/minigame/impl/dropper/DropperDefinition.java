package dev.frost.miniverse.minigame.impl.dropper;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.map.MapGamemodeRegistry;
import dev.frost.miniverse.map.MapGamemodeType;
import dev.frost.miniverse.map.MapStore;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.MarkerGrouping;
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

public final class DropperDefinition implements MinigameDefinition {
    public static final String ID = "dropper";
    public static final String DISPLAY_NAME = "Dropper";

    public static final String LEVEL_CONFIG = "level_config";
    public static final String LEVEL_SPAWN = "level_spawn";
    public static final String LEVEL_GOAL = "level_goal";
    public static final String LOBBY_SPAWN = "lobby_spawn";

    public static final MapEditorExtension EXTENSION = new MapEditorExtension(
        ID, DISPLAY_NAME,
        List.of(
            new MarkerDefinition(
                LEVEL_CONFIG, "Dropper Level", MarkerType.POINT, "levels", 1, 100,
                null, null,
                "Defines a dropper level within the map course.\n" +
                "• Required: 1 to 100 levels.\n" +
                "• Purpose: Parent container for a single drop challenge. Drill down into this level to place its Spawn Point and Goal Region.\n" +
                "• Placement: Place at the top entrance of the dropper shaft. Name it (e.g. 'Level 1: Industrial', 'Level 2: Atlantis')."
            ),
            new MarkerDefinition(
                LEVEL_SPAWN, "Level Spawn", MarkerType.POINT, "levelSpawns", 1, 100,
                null, MarkerGrouping.logical(LEVEL_CONFIG, "levelId"),
                "Drop starting platform for this specific level.\n" +
                "• Required: 1 to 100 points (at least 1 per level).\n" +
                "• Purpose: Players teleport here when starting or restarting this dropper level before leaping into the drop shaft.\n" +
                "• Placement: Place on the starting jump ledge at the very top of the dropper shaft facing downward into the obstacles."
            ),
            new MarkerDefinition(
                LEVEL_GOAL, "Level Goal", MarkerType.REGION, "levelGoals", 1, 100,
                List.of(TriggerType.PLAYER_ENTER), MarkerGrouping.logical(LEVEL_CONFIG, "levelId"),
                "Landing pool volume at the bottom of the level shaft.\n" +
                "• Required: 1 to 100 regions (at least 1 per level).\n" +
                "• Purpose: Landing safely inside this region triggers level completion and advances the player to the next level.\n" +
                "• Placement: Define a 3D box enclosing the water pool, slime blocks, or cobweb landing zone at the base of the drop."
            ),
            new MarkerDefinition(
                LOBBY_SPAWN, "Lobby Spawn", MarkerType.POINT, "lobbySpawns", 0, 16,
                null, null,
                "Pre-game waiting area and post-game winners lounge.\n" +
                "• Optional: Up to 16 points.\n" +
                "• Purpose: Players spawn here before the match begins and return here upon completing all levels in the dropper map.\n" +
                "• Placement: Place in an open lobby, spectator platform, or trophy room overlooking the dropper shafts."
            )
        ),
        List.of(DropperMapConfig::validateEditor)
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
            "Drop through obstacles to reach the water pool at the bottom!",
            "🪂",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        DropperSettings parsed = DropperSettings.fromNbt(settings);
        parsed.writeTo(properties);
        if (!parsed.mapId().isBlank()) {
            MapStore.readGamemodeConfig(parsed.mapId(), ID)
                .ifPresent(config -> properties.setProperty("dropper.mapConfig", config.toString()));
        }
    }

    @Override
    public void writeLaunchProperties(NbtCompound settings, Map<String, String> properties) {
        DropperSettings parsed = DropperSettings.fromNbt(settings);
        if (!parsed.mapId().isBlank()) {
            properties.put("miniverse.dropper.mapId", parsed.mapId());
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("dropper")
                .then(CommandManager.literal("skip")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) {
                            context.getSource().sendFeedback(() -> Text.literal("Only players can execute /dropper skip"), false);
                            return 0;
                        }
                        if (MinigameManager.getInstance().getActiveMinigame() instanceof DropperMinigame dm) {
                            return dm.handleCommandSkip(player);
                        }
                        context.getSource().sendFeedback(() -> Text.literal("Dropper is not currently active.").formatted(Formatting.RED), false);
                        return 0;
                    })
                )
        );
    }

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, DropperMapConfig::validate));
        MapEditorExtensionRegistry.register(EXTENSION);
        DropperSessionBootstrap.register();
    }
}
