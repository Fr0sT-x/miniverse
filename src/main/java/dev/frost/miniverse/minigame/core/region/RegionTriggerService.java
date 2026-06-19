package dev.frost.miniverse.minigame.core.region;

import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;
import dev.frost.miniverse.map.editor.MapMarker;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.MarkerType;
import dev.frost.miniverse.map.region.TriggerType;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.event.PlayerRegionAware;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class RegionTriggerService {
    private static final RegionTriggerService INSTANCE = new RegionTriggerService();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RegionTriggerService.class);

    private final Map<UUID, Set<String>> activeRegionsPerPlayer = new HashMap<>();

    private RegionTriggerService() {
    }

    public static RegionTriggerService getInstance() {
        return INSTANCE;
    }

    public void tick(MinecraftServer server) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active == null) {
            this.clear();
            return;
        }

        dev.frost.miniverse.map.runtime.RuntimeMarkerCache.getInstance().tick();

        boolean dispatchEvents = active instanceof PlayerRegionAware;
        PlayerRegionAware regionAware = dispatchEvents ? (PlayerRegionAware) active : null;

        MinigameContext context = MinigameManager.getInstance().getContext();
        if (context == null) return;

        for (ServerPlayerEntity player : context.liveParticipants()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            
            Set<String> currentlyInside = new HashSet<>();
            Box playerBox = player.getBoundingBox();
            
            ChunkPos pos = player.getChunkPos();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    ChunkPos neighbor = new ChunkPos(pos.x + x, pos.z + z);
                    List<MapMarker> markers = dev.frost.miniverse.map.runtime.RuntimeMarkerCache.getInstance().getRegionsIntersecting(neighbor);
                    if (markers == null || markers.isEmpty()) continue;
                    
                    for (MapMarker marker : markers) {
                        if (intersects(playerBox, marker)) {
                            currentlyInside.add(marker.id());
                            
                            Set<String> activeRegions = this.activeRegionsPerPlayer.computeIfAbsent(player.getUuid(), k -> new HashSet<>());
                            if (activeRegions.add(marker.id())) {
                                if (dispatchEvents && hasTrigger(marker, TriggerType.PLAYER_ENTER)) {
                                    regionAware.onPlayerEnterRegion(player, marker);
                                }
                            }
                        }
                    }
                }
            }
            
            Set<String> activeRegions = this.activeRegionsPerPlayer.computeIfAbsent(player.getUuid(), k -> new HashSet<>());
            List<String> toRemove = new ArrayList<>();
            for (String activeId : activeRegions) {
                if (!currentlyInside.contains(activeId)) {
                    toRemove.add(activeId);
                    if (dispatchEvents) {
                        MapMarker exited = findMarker(activeId);
                        if (exited != null && hasTrigger(exited, TriggerType.PLAYER_EXIT)) {
                            regionAware.onPlayerExitRegion(player, exited);
                        }
                    }
                }
            }
            activeRegions.removeAll(toRemove);
        }
    }

    private boolean intersects(Box box, MapMarker marker) {
        for (dev.frost.miniverse.map.editor.RegionPart part : marker.regions()) {
            dev.frost.miniverse.map.MapPosition p1 = part.min();
            dev.frost.miniverse.map.MapPosition p2 = part.max();
            double minX = Math.min(p1.x(), p2.x());
            double minY = Math.min(p1.y(), p2.y());
            double minZ = Math.min(p1.z(), p2.z());
            double maxX = Math.max(p1.x(), p2.x()) + 1.0;
            double maxY = Math.max(p1.y(), p2.y()) + 1.0;
            double maxZ = Math.max(p1.z(), p2.z()) + 1.0;
            if (box.intersects(minX, minY, minZ, maxX, maxY, maxZ)) return true;
        }
        return false;
    }
    
    private MapMarker findMarker(String id) {
        return dev.frost.miniverse.map.runtime.RuntimeMarkerCache.getInstance().findMarker(id);
    }
    
    private boolean hasTrigger(MapMarker marker, TriggerType type) {
        java.util.Optional<com.google.gson.JsonObject> sessionJson = dev.frost.miniverse.session.SessionRuntimeConfig.getSessionJson();
        if (sessionJson.isEmpty()) return false;
        
        com.google.gson.JsonObject root = sessionJson.get();
        String gameId = dev.frost.miniverse.session.SessionConfigJson.string(root, "gameId", "");
        if (gameId.isBlank()) {
            gameId = dev.frost.miniverse.session.SessionConfigJson.string(root, "game", "");
        }
        if (gameId.isBlank()) {
            gameId = System.getProperty("miniverse.session.game", "");
        }
        
        MapEditorExtension extension = dev.frost.miniverse.map.editor.MapEditorExtensionRegistry.get(gameId).orElse(null);
        if (extension == null) return false;
        for (dev.frost.miniverse.map.editor.MarkerDefinition def : extension.markers()) {
            if (def.key().equals(marker.definitionKey())) {
                return def.triggers().contains(type);
            }
        }
        return false;
    }

    private void clear() {
        this.activeRegionsPerPlayer.clear();
    }
}
