package dev.frost.miniverse.map;

import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.session.BackendLaunchMode;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;

import java.util.Locale;
import java.util.Optional;

/**
 * Centralized world rules and environmental controls for maps and the Map Editor.
 *
 * <p>Handles:
 * <ul>
 *   <li>Leaf decay prevention on all maps and in the Map Editor.</li>
 *   <li>Map Editor world rules (locked daylight, clear weather, zero mob spawns, zero fire spread).</li>
 * </ul>
 */
public final class MapWorldRules {

    private MapWorldRules() {
    }

    /**
     * Checks if the world is part of a map (either actively being edited in the Map Editor
     * or being played in any map-based session / gamemode).
     *
     * @param world the server world to check
     * @return true if the world is a map environment
     */
    public static boolean isMapWorld(ServerWorld world) {
        // 1. Map Editor or Inspection Mode
        BackendLaunchMode launchMode = SessionRuntimeConfig.getLaunchMode();
        if (launchMode == BackendLaunchMode.MAP_EDITOR || launchMode == BackendLaunchMode.INSPECTION_SESSION) {
            return true;
        }

        // 2. Session properties have map.id set
        String mapId = SessionRuntimeConfig.getProperty("map.id", "");
        if (!mapId.isBlank()) {
            return true;
        }

        // 3. Session JSON has mapId or mapEditor configured
        Optional<JsonObject> sessionJson = SessionRuntimeConfig.getSessionJson();
        if (sessionJson.isPresent()) {
            JsonObject json = sessionJson.get();
            if (json.has("mapId") && !json.get("mapId").getAsString().isBlank()) {
                return true;
            }
            if (json.has("mapEditor")) {
                return true;
            }
        }

        // 4. System property game ID or session property game ID is registered in MapGamemodeRegistry
        String gameId = System.getProperty("miniverse.session.game", "");
        if (gameId.isBlank()) {
            gameId = SessionRuntimeConfig.getProperty("game", "");
        }
        if (!gameId.isBlank() && MapGamemodeRegistry.get(gameId).isPresent()) {
            return true;
        }

        // 5. Active minigame runtime check
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null && runtime.minigame() != null) {
            String runtimeGame = runtime.minigame().getName().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if (MapGamemodeRegistry.get(runtimeGame).isPresent()) {
                return true;
            }
            // Check by block protection provider
            if (runtime.minigame() instanceof dev.frost.miniverse.minigame.core.protection.BlockProtectionProvider) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether leaf decay should be disabled in the given world.
     * Leaf decay is completely disabled in the Map Editor and across any map-based gamemode,
     * preserving decorative leaves, hedges, trees, and obstacles permanently.
     *
     * <p>Survival/vanilla-world minigames (e.g. Speedrun, Manhunt) return false, preserving
     * standard vanilla leaf decay.
     *
     * @param world the world being ticked
     * @return true if leaf decay should be prevented
     */
    public static boolean isLeafDecayDisabled(ServerWorld world) {
        return isMapWorld(world);
    }

    /**
     * Applies optimal builder rules for the Map Editor server.
     * Ensures clean daylight, clear skies, and no environmental or mob interference.
     *
     * @param server the active MinecraftServer
     */
    public static void applyMapEditorRules(MinecraftServer server) {
        if (server == null) {
            return;
        }

        for (ServerWorld world : server.getWorlds()) {
            GameRules rules = world.getGameRules();
            rules.get(GameRules.DO_MOB_SPAWNING).set(false, server);
            rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            rules.get(GameRules.DO_WEATHER_CYCLE).set(false, server);
            rules.get(GameRules.DO_FIRE_TICK).set(false, server);
            rules.get(GameRules.DO_VINES_SPREAD).set(false, server);
            rules.get(GameRules.DO_INSOMNIA).set(false, server);
            rules.get(GameRules.DO_PATROL_SPAWNING).set(false, server);
            rules.get(GameRules.DO_TRADER_SPAWNING).set(false, server);
            world.setTimeOfDay(6000); // Fixed noon daylight
            world.setWeather(0, 0, false, false); // Clear weather
        }
    }
}
