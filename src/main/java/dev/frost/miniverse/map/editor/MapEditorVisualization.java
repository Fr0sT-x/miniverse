package dev.frost.miniverse.map.editor;

import dev.frost.miniverse.map.MapPosition;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;

public final class MapEditorVisualization {
    private MapEditorVisualization() {
    }

    public static void render(MinecraftServer server, String mapId) {
        if (mapId == null || mapId.isBlank() || server.getTicks() % 10 != 0) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            renderFor(player, mapId);
        }
    }

    private static void renderFor(ServerPlayerEntity player, String mapId) {
        for (MapEditorExtension extension : MapEditorExtensionRegistry.all()) {
            Map<String, List<MapMarker>> markers = MapEditorMarkerStore.loadAll(mapId, extension);
            for (MarkerDefinition definition : extension.markers()) {
                for (MapMarker marker : markers.getOrDefault(definition.key(), List.of())) {
                    if (definition.type() == MarkerType.REGION) {
                        renderRegion(player, marker);
                    } else if (definition.type() == MarkerType.MULTI_POINT) {
                        renderMultiPoint(player, marker);
                    } else {
                        renderPoint(player, marker);
                    }
                }
            }
        }
    }

    private static void renderPoint(ServerPlayerEntity player, MapMarker marker) {
    }

    private static void renderRegion(ServerPlayerEntity player, MapMarker marker) {
    }

    private static void renderMultiPoint(ServerPlayerEntity player, MapMarker marker) {
    }

    private static void renderLine(ServerPlayerEntity player, double ax, double ay, double az, double bx, double by, double bz) {
    }
}
