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
        if (marker.points().isEmpty()) {
            return;
        }
        MapPosition point = marker.points().getFirst();
        player.getServerWorld().spawnParticles(player, ParticleTypes.END_ROD, true, point.x(), point.y(), point.z(), 2, 0.15D, 0.45D, 0.15D, 0.0D);
        for (int i = 0; i < 6; i++) {
            player.getServerWorld().spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, true, point.x(), point.y() + i, point.z(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void renderRegion(ServerPlayerEntity player, MapMarker marker) {
        if (marker.points().size() < 2) {
            return;
        }
        MapPosition min = marker.points().get(0);
        MapPosition max = marker.points().get(1);
        double minX = min.x();
        double minY = min.y();
        double minZ = min.z();
        double maxX = max.x() + 1.0D;
        double maxY = max.y() + 1.0D;
        double maxZ = max.z() + 1.0D;
        renderLine(player, minX, minY, minZ, maxX, minY, minZ);
        renderLine(player, minX, minY, maxZ, maxX, minY, maxZ);
        renderLine(player, minX, maxY, minZ, maxX, maxY, minZ);
        renderLine(player, minX, maxY, maxZ, maxX, maxY, maxZ);
        renderLine(player, minX, minY, minZ, minX, maxY, minZ);
        renderLine(player, maxX, minY, minZ, maxX, maxY, minZ);
        renderLine(player, minX, minY, maxZ, minX, maxY, maxZ);
        renderLine(player, maxX, minY, maxZ, maxX, maxY, maxZ);
        renderLine(player, minX, minY, minZ, minX, minY, maxZ);
        renderLine(player, maxX, minY, minZ, maxX, minY, maxZ);
        renderLine(player, minX, maxY, minZ, minX, maxY, maxZ);
        renderLine(player, maxX, maxY, minZ, maxX, maxY, maxZ);
    }

    private static void renderMultiPoint(ServerPlayerEntity player, MapMarker marker) {
        List<MapPosition> points = marker.points();
        for (MapPosition point : points) {
            player.getServerWorld().spawnParticles(player, ParticleTypes.END_ROD, true, point.x(), point.y(), point.z(), 2, 0.12D, 0.25D, 0.12D, 0.0D);
        }
        for (int i = 1; i < points.size(); i++) {
            MapPosition a = points.get(i - 1);
            MapPosition b = points.get(i);
            renderLine(player, a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
        }
    }

    private static void renderLine(ServerPlayerEntity player, double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        int steps = Math.max(2, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 2.0D));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            player.getServerWorld().spawnParticles(player, ParticleTypes.CRIT, true, ax + dx * t, ay + dy * t, az + dz * t, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
