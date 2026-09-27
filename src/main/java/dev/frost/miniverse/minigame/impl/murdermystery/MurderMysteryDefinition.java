package dev.frost.miniverse.minigame.impl.murdermystery;

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

public final class MurderMysteryDefinition implements MinigameDefinition {
    public static final String ID = "murdermystery";
    public static final String DISPLAY_NAME = "Murder Mystery";

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
            "Find the murderer before it's too late! One murderer, many innocents, and a detective to save them.",
            "🔪",
            this.topology()
        );
    }
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        MurderMysterySettings parsed = MurderMysterySettings.fromNbt(settings);
        parsed.writeTo(properties);
        if (!parsed.mapId().isBlank()) {
            dev.frost.miniverse.map.MapStore.readGamemodeConfig(parsed.mapId(), ID)
                .ifPresent(config -> properties.setProperty("murdermystery.mapConfig", config.toString()));
        }
    }

    @Override
    public void writeLaunchProperties(NbtCompound settings, Map<String, String> properties) {
        MurderMysterySettings parsed = MurderMysterySettings.fromNbt(settings);
        if (!parsed.mapId().isBlank()) {
            properties.put("miniverse.murdermystery.mapId", parsed.mapId());
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
    }

    public static final MapEditorExtension EXTENSION = new MapEditorExtension(
        ID,
        DISPLAY_NAME,
        List.of(
            new MarkerDefinition(
                "spawn_point", "Spawn Point", MarkerType.POINT, "spawnPoints", 3, Integer.MAX_VALUE, null,
                "Randomized spawn points for innocents, detectives, and murderers.\n" +
                "• Required: At least 3 points (3 or more; 10 to 24 recommended).\n" +
                "• Purpose: Players spawn across these locations at round start so roles remain hidden and distributed.\n" +
                "• Placement: Scatter throughout the map across corridors, rooms, and open squares away from direct line-of-sight."
            ),
            new MarkerDefinition(
                "coin_spawn", "Coin Spawn", MarkerType.POINT, "coinSpawns", 1, Integer.MAX_VALUE, null,
                "Periodic gold coin spawn locations across the map.\n" +
                "• Required: At least 1 point (1 or more; 15 to 30 recommended).\n" +
                "• Purpose: Gold ingots spawn here periodically. Innocents collecting 10 coins receive a defensive one-shot bow.\n" +
                "• Placement: Place on floor tiles in hallways, dead-ends, secret rooms, and high-risk central courtyards."
            ),
            new MarkerDefinition(
                "shop_npc", "Shop NPC", MarkerType.POINT, "shopNpcs", 1, Integer.MAX_VALUE, null,
                "Merchant NPC offering utility items and special perks.\n" +
                "• Required: At least 1 point (1 or more).\n" +
                "• Purpose: Players can interact with this NPC to purchase detective tracking clues, speed boosts, or defensive shields.\n" +
                "• Placement: Place against a wall or counter in a recognizable shop building or central marketplace."
            )
        ),
        List.of()
    );

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, (map, config) -> dev.frost.miniverse.map.MapValidationResult.ok()));
        MapEditorExtensionRegistry.register(EXTENSION);
        MurderMysteryGameEvents.register();
        MurderMysterySessionBootstrap.bootstrap();
    }
}
