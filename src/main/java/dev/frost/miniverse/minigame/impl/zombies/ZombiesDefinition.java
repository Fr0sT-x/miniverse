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
            new MarkerDefinition(
                "player_spawn", "Player Spawn", MarkerType.POINT, "playerSpawns", 1, 16, null, null,
                "Survivor starting spawn positions in the first room (e.g. Alley).\n" +
                "• Required: 1 to 16 points (at least 1; 4 recommended).\n" +
                "• Purpose: All surviving players spawn here at Round 1 with basic starting pistols.\n" +
                "• Placement: Place on solid floor blocks inside the designated starting area facing into the room."
            ),
            new MarkerDefinition(
                "area_config", "Area", MarkerType.POINT, "areaConfigs", 1, 32, null, null,
                "Defines a named district or room (e.g. Alley, Hotel, Office, Power Station).\n" +
                "• Required: 1 to 32 areas.\n" +
                "• Purpose: Used to control zombie wave routing, room unlock progression, and window spawn grouping.\n" +
                "• Placement: Place in the center of the room. Set 'isStartArea' to true for the starting room in Properties."
            ),
            new MarkerDefinition(
                "door", "Door", MarkerType.REGION, "doors", 0, 64, null, null,
                "Debris barricade or locked iron door separating two map areas.\n" +
                "• Optional: Up to 64 doors (0 to 64).\n" +
                "• Purpose: Players purchase doors with gold to unlock new areas and weapons. Disappears when opened.\n" +
                "• Placement: Create a 3D box enclosing the doorway blocks. Set properties 'area1', 'area2', and 'gold' price."
            ),
            new MarkerDefinition(
                "door_closed", "Door Closed Template", MarkerType.REGION, "doorClosedTemplates", 0, 64, null,
                MarkerGrouping.logical("door", "doorId"),
                "Block template copied into the doorway when the door is closed.\n" +
                "• Optional: Up to 64 regions (child of Door).\n" +
                "• Purpose: Stores the exact block arrangement (iron bars, oak planks, fences) to restore when a game resets.\n" +
                "• Placement: Define a region matching the closed door blocks, linked to its parent Door marker."
            ),
            new MarkerDefinition(
                "window_spawn", "Window Zombie Spawn", MarkerType.POINT, "windowSpawns", 1, 128, null, null,
                "Zombie spawn location outside a breakable barricade window.\n" +
                "• Required: 1 to 128 points (at least 1).\n" +
                "• Purpose: Zombies spawn outside this window and attack the wooden barricade boards to break into the room.\n" +
                "• Placement: Place 1 to 2 blocks outside the barricade facing inward towards the player room. Set 'area' property."
            ),
            new MarkerDefinition(
                "window_blocks", "Window Barricade", MarkerType.REGION, "windowBlocks", 1, 128, null,
                MarkerGrouping.logical("window_spawn", "windowId"),
                "The destructible wooden barricade slabs covering the window.\n" +
                "• Required: 1 to 128 regions (child of Window Zombie Spawn).\n" +
                "• Purpose: Zombies progressively tear down these boards. Players repair them by standing in the repair zone.\n" +
                "• Placement: Select the bounding box of the 2 to 3 wooden slab blocks forming the barricade."
            ),
            new MarkerDefinition(
                "window_repair", "Window Repair Zone", MarkerType.REGION, "windowRepairZones", 1, 128, null,
                MarkerGrouping.logical("window_spawn", "windowId"),
                "Area inside the room where players stand to repair the window barricade.\n" +
                "• Required: 1 to 128 regions (child of Window Zombie Spawn).\n" +
                "• Purpose: Standing or sneaking in this region rebuilds torn boards one by one and awards gold to the player.\n" +
                "• Placement: Define a 2x2 or 1x1 region on the floor immediately in front of the inside of the window barricade."
            ),
            new MarkerDefinition(
                "weapon_shop", "Weapon Shop", MarkerType.POINT, "weaponShops", 0, 32, null, null,
                "Wall-mounted weapon purchase station chalk outline.\n" +
                "• Optional: Up to 32 points.\n" +
                "• Purpose: Players interact to buy a weapon (e.g. Shotgun, Rifle) or refill ammunition for gold.\n" +
                "• Placement: Place against a wall at eye level. Set properties: 'weapon', 'purchasePrice', and 'refillPrice'."
            ),
            new MarkerDefinition(
                "armor_shop", "Armor Shop", MarkerType.POINT, "armorShops", 0, 32, null, null,
                "Wall-mounted armor purchase station.\n" +
                "• Optional: Up to 32 points.\n" +
                "• Purpose: Players interact to buy leather, gold, iron, or diamond armor pieces for gold.\n" +
                "• Placement: Place against a wall at eye level. Set properties: 'part' (helmet/chest/etc.), 'quality', and 'price'."
            ),
            new MarkerDefinition(
                "perk_machine", "Perk Machine", MarkerType.POINT, "perkMachines", 0, 16, null, null,
                "Perk Cola vending machine station.\n" +
                "• Optional: Up to 16 points.\n" +
                "• Purpose: Players spend gold to gain permanent perks (Quick Revive, Speed Cola, Juggernog, Extra Weapon).\n" +
                "• Placement: Place against a wall on a 1x2 or 2x2 pedestal. Set properties: 'perk' type and 'gold' cost."
            ),
            new MarkerDefinition(
                "lucky_chest", "Lucky Chest", MarkerType.POINT, "luckyChests", 1, 16, null, null,
                "Mystery Box chest station that spins for random weapons.\n" +
                "• Required: 1 to 16 points (at least 1).\n" +
                "• Purpose: Spawns the Mystery Box. Players pay gold to roll for high-tier weapons, ray guns, or teddy bears.\n" +
                "• Placement: Place on top of a 2x1 table or pedestal. Set property: 'gold' roll cost."
            ),
            new MarkerDefinition(
                "power_switch", "Power Switch", MarkerType.POINT, "powerSwitches", 1, 4, null, null,
                "Master power switch station activating electricity across the map.\n" +
                "• Required: 1 to 4 points (at least 1).\n" +
                "• Purpose: Turning on the power enables perk machines, electric traps, and the Ultimate Machine.\n" +
                "• Placement: Place on a prominent wall in the Power Station or main reactor room. Set 'gold' cost (usually 0)."
            ),
            new MarkerDefinition(
                "team_machine", "Team Machine", MarkerType.POINT, "teamMachines", 0, 4, null, null,
                "Team support station dispensing team-wide ammo or health.\n" +
                "• Optional: Up to 4 points.\n" +
                "• Purpose: Players can activate this station to restore health or replenish team ammo caches during emergencies.\n" +
                "• Placement: Place in a defensible central bunker or medical station."
            ),
            new MarkerDefinition(
                "ultimate_machine", "Ultimate Machine", MarkerType.POINT, "ultimateMachines", 0, 4, null, null,
                "Pack-a-Punch weapon upgrade station.\n" +
                "• Optional: Up to 4 points.\n" +
                "• Purpose: Upgrades currently held weapons with massive damage boosts, larger magazines, and custom effects.\n" +
                "• Placement: Place in an end-game secret room or unlocked laboratory. Set property: 'gold' upgrade cost."
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
