package dev.frost.miniverse.minigame.impl.zombies;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.map.MapGamemodeRegistry;
import dev.frost.miniverse.map.MapGamemodeType;
import dev.frost.miniverse.map.MapStore;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.MarkerGrouping;
import dev.frost.miniverse.map.editor.MarkerType;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.minigame.impl.zombies.command.ZombiesCommand;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesMapConfig;
import dev.frost.miniverse.session.SessionTopology;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class ZombiesDefinition implements MinigameDefinition {
    public static final String ID = "zombies";
    public static final String DISPLAY_NAME = "Zombies";

    public static final MapEditorExtension EXTENSION = new MapEditorExtension(
        ID, DISPLAY_NAME,
        List.of(
            new MarkerDefinition("player_spawn",       "Player Spawn",         MarkerType.POINT,  "playerSpawns",         1, 16,  null, null, "Spawn point for survivors in the starting area (Alley)."),
            new MarkerDefinition("area_config",        "Area",                 MarkerType.POINT,  "areaConfigs",          1, 32,  null, null, "Named map area (e.g. Alley, Hotel). Set 'isStartArea' to true for Alley."),
            new MarkerDefinition("door",               "Door",                 MarkerType.REGION, "doors",                0, 64,  null, null, "3D region of the door. Set properties 'area1', 'area2', 'gold'."),
            new MarkerDefinition("door_closed",        "Door Closed Template", MarkerType.REGION, "doorClosedTemplates", 0, 64,  null, MarkerGrouping.logical("door", "doorId"), "Blocks copied to fill the door when closed."),
            new MarkerDefinition("window_spawn",       "Window Zombie Spawn",  MarkerType.POINT,  "windowSpawns",         1, 128, null, null, "Spawn location outside window facing in. Set property 'area'."),
            new MarkerDefinition("window_blocks",      "Window Barricade",     MarkerType.REGION, "windowBlocks",         1, 128, null, MarkerGrouping.logical("window_spawn", "windowId"), "Barricade slab blocks attacked by zombies."),
            new MarkerDefinition("window_repair",      "Window Repair Zone",   MarkerType.REGION, "windowRepairZones",    1, 128, null, MarkerGrouping.logical("window_spawn", "windowId"), "Area where players sneak to repair barricades."),
            new MarkerDefinition("weapon_shop",        "Weapon Shop",          MarkerType.POINT,  "weaponShops",          0, 32,  null, null, "Wall weapon station. Properties: weapon, purchasePrice, refillPrice."),
            new MarkerDefinition("armor_shop",         "Armor Shop",           MarkerType.POINT,  "armorShops",           0, 32,  null, null, "Wall armor station. Properties: part, quality, price."),
            new MarkerDefinition("perk_machine",       "Perk Machine",         MarkerType.POINT,  "perkMachines",         0, 16,  null, null, "Perk machine station. Properties: perk, gold."),
            new MarkerDefinition("lucky_chest",        "Lucky Chest",          MarkerType.POINT,  "luckyChests",          1, 16,  null, null, "Mystery Box chest position. Property: gold."),
            new MarkerDefinition("power_switch",       "Power Switch",         MarkerType.POINT,  "powerSwitches",        1, 4,   null, null, "Power switch station. Property: gold."),
            new MarkerDefinition("team_machine",       "Team Machine",         MarkerType.POINT,  "teamMachines",         0, 4,   null, null, "Team Machine station."),
            new MarkerDefinition("ultimate_machine",   "Ultimate Machine",     MarkerType.POINT,  "ultimateMachines",     0, 4,   null, null, "Weapon upgrade station. Property: gold.")
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
            "Survive 30 waves of zombies, unlock areas, purchase perks and weapons, and defeat the Broodmother.",
            "🧟",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settings, Properties properties) {
        ZombiesSettings parsed = ZombiesSettings.fromNbt(settings);
        parsed.writeTo(properties);
        if (!parsed.mapId().isBlank()) {
            MapStore.readGamemodeConfig(parsed.mapId(), ID)
                .ifPresent(config -> properties.setProperty("zombies.mapConfig", config.toString()));
        }
    }

    @Override
    public void writeLaunchProperties(NbtCompound settings, Map<String, String> properties) {
        ZombiesSettings parsed = ZombiesSettings.fromNbt(settings);
        if (!parsed.mapId().isBlank()) {
            properties.put("miniverse.zombies.mapId", parsed.mapId());
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        ZombiesCommand.register(dispatcher);
    }

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, ZombiesMapConfig::validateEditor));
        MapEditorExtensionRegistry.register(EXTENSION);
        ZombiesSessionBootstrap.register();

        NetworkConstants.registerPayloadTypes();
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.ZOMBIES_RELOAD_ID, (payload, context) -> {
            context.player().server.execute(() -> {
                if (MinigameManager.getInstance().getActiveMinigame() instanceof ZombiesMinigame zm) {
                    zm.handleReloadKey(context.player());
                }
            });
        });
    }
}
