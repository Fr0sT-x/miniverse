package dev.frost.miniverse.map.editor;

import dev.frost.miniverse.map.MapPosition;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.List;
import java.util.Map;

public final class MapEditorNbt {
    private MapEditorNbt() {
    }

    public static NbtList extensionsToNbt() {
        NbtList list = new NbtList();
        for (MapEditorExtension extension : MapEditorExtensionRegistry.all()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("gameId", extension.gameId());
            entry.putString("displayName", extension.displayName());
            NbtList markers = new NbtList();
            for (MarkerDefinition definition : extension.markers()) {
                NbtCompound marker = new NbtCompound();
                marker.putString("key", definition.key());
                marker.putString("displayName", definition.displayName());
                marker.putString("type", definition.type().name());
                marker.putString("configKey", definition.configKey());
                marker.putString("description", definition.description());
                marker.putInt("minCount", definition.minCount());
                marker.putInt("maxCount", definition.maxCount() == Integer.MAX_VALUE ? -1 : definition.maxCount());
                NbtList triggerList = new NbtList();
                for (dev.frost.miniverse.map.region.TriggerType trigger : definition.triggers()) {
                    triggerList.add(net.minecraft.nbt.NbtString.of(trigger.name()));
                }
                marker.put("triggers", triggerList);
                markers.add(marker);
            }
            entry.put("markers", markers);
            list.add(entry);
        }
        return list;
    }

    public static NbtCompound editorStateToNbt(String mapId) {
        NbtCompound root = new NbtCompound();
        root.putString("mapId", mapId == null ? "" : mapId);
        NbtList games = new NbtList();

        java.util.Optional<dev.frost.miniverse.map.MapDescriptor> mapOpt = dev.frost.miniverse.map.MapStore.find(mapId);

        for (MapEditorExtension extension : MapEditorExtensionRegistry.all()) {
            NbtCompound game = new NbtCompound();
            game.putString("gameId", extension.gameId());

            com.google.gson.JsonObject config = mapOpt.flatMap(m -> dev.frost.miniverse.map.MapStore.readGamemodeConfig(m, extension.gameId())).orElseGet(com.google.gson.JsonObject::new);
            Map<String, List<MapMarker>> markersByDefinition = MapEditorMarkerStore.loadAll(config, extension);

            NbtList markerGroups = new NbtList();
            for (MarkerDefinition definition : extension.markers()) {
                NbtCompound group = new NbtCompound();
                group.putString("definitionKey", definition.key());
                NbtList markers = new NbtList();
                for (MapMarker marker : markersByDefinition.getOrDefault(definition.key(), List.of())) {
                    markers.add(markerToNbt(marker));
                }
                group.put("markers", markers);
                markerGroups.add(group);
            }
            game.put("markerGroups", markerGroups);

            NbtCompound validationNbt = new NbtCompound();
            dev.frost.miniverse.map.MapValidationResult result = mapOpt.map(m -> dev.frost.miniverse.map.MapStore.validate(m, extension.gameId())).orElseGet(() -> dev.frost.miniverse.map.MapStore.validate(mapId, extension.gameId()));
            validationNbt.putBoolean("valid", result.valid());
            NbtList errors = new NbtList();
            for (String error : result.errors()) {
                errors.add(net.minecraft.nbt.NbtString.of(error));
            }
            validationNbt.put("errors", errors);
            NbtList warnings = new NbtList();
            for (String warning : result.warnings()) {
                warnings.add(net.minecraft.nbt.NbtString.of(warning));
            }
            validationNbt.put("warnings", warnings);
            game.put("validation", validationNbt);

            games.add(game);
        }
        root.put("games", games);
        return root;
    }

    private static NbtCompound markerToNbt(MapMarker marker) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", marker.id());
        nbt.putString("definitionKey", marker.definitionKey());
        nbt.putString("name", marker.name());
        nbt.putString("type", marker.type().name());
        NbtList points = new NbtList();
        for (MapPosition point : marker.points()) {
            NbtCompound pointNbt = new NbtCompound();
            pointNbt.putDouble("x", point.x());
            pointNbt.putDouble("y", point.y());
            pointNbt.putDouble("z", point.z());
            pointNbt.putFloat("yaw", point.yaw());
            pointNbt.putFloat("pitch", point.pitch());
            points.add(pointNbt);
        }
        nbt.put("points", points);
        
        NbtList regions = new NbtList();
        for (RegionPart region : marker.regions()) {
            NbtCompound regionNbt = new NbtCompound();
            
            NbtCompound minNbt = new NbtCompound();
            minNbt.putDouble("x", region.min().x());
            minNbt.putDouble("y", region.min().y());
            minNbt.putDouble("z", region.min().z());
            minNbt.putFloat("yaw", region.min().yaw());
            minNbt.putFloat("pitch", region.min().pitch());
            regionNbt.put("min", minNbt);
            
            NbtCompound maxNbt = new NbtCompound();
            maxNbt.putDouble("x", region.max().x());
            maxNbt.putDouble("y", region.max().y());
            maxNbt.putDouble("z", region.max().z());
            maxNbt.putFloat("yaw", region.max().yaw());
            maxNbt.putFloat("pitch", region.max().pitch());
            regionNbt.put("max", maxNbt);
            
            regions.add(regionNbt);
        }
        nbt.put("regions", regions);
        
        if (marker.properties() != null && !marker.properties().isEmpty()) {
            nbt.putString("properties", marker.properties().toString());
        }
        return nbt;
    }
}
