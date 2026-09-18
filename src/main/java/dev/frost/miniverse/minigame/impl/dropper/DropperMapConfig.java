package dev.frost.miniverse.minigame.impl.dropper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.map.MapDescriptor;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.RegionPart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DropperMapConfig(
    List<DropperLevel> levels,
    List<MapPosition> lobbySpawns
) {
    public DropperMapConfig {
        levels = levels == null ? List.of() : List.copyOf(levels);
        lobbySpawns = lobbySpawns == null ? List.of() : List.copyOf(lobbySpawns);
    }

    public record DropperLevel(
        String id,
        String name,
        MapPosition spawn,
        String goalMarkerId,
        List<RegionPart> goalRegions
    ) {
        public DropperLevel {
            goalRegions = goalRegions == null ? List.of() : List.copyOf(goalRegions);
        }
    }

    private static class LevelDraft {
        final String id;
        final String name;
        MapPosition spawn;
        String goalMarkerId = "";
        List<RegionPart> goalRegions = new ArrayList<>();

        LevelDraft(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static DropperMapConfig fromJson(JsonObject json) {
        if (json == null) {
            return new DropperMapConfig(List.of(), List.of());
        }

        Map<String, LevelDraft> drafts = new LinkedHashMap<>();

        // 1. Parse level_config markers (parent levels)
        if (json.has("levels") && json.get("levels").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("levels")) {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    String id = obj.has("id") ? obj.get("id").getAsString() : "";
                    String name = obj.has("name") ? obj.get("name").getAsString() : "Level";
                    if (!id.isBlank()) {
                        drafts.put(id, new LevelDraft(id, name));
                    }
                }
            }
        }

        // 2. Parse levelSpawns (grouped logically by levelId)
        if (json.has("levelSpawns") && json.get("levelSpawns").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("levelSpawns")) {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    String levelId = extractLevelId(obj);
                    MapPosition pos = extractPoint(obj);
                    if (pos != null) {
                        if (!levelId.isBlank() && drafts.containsKey(levelId)) {
                            drafts.get(levelId).spawn = pos;
                        } else if (drafts.size() == 1) {
                            drafts.values().iterator().next().spawn = pos;
                        }
                    }
                }
            }
        }

        // 3. Parse levelGoals (grouped logically by levelId)
        if (json.has("levelGoals") && json.get("levelGoals").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("levelGoals")) {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    String levelId = extractLevelId(obj);
                    String markerId = obj.has("id") ? obj.get("id").getAsString() : "";
                    List<RegionPart> regions = extractRegions(obj);
                    if (!levelId.isBlank() && drafts.containsKey(levelId)) {
                        drafts.get(levelId).goalMarkerId = markerId;
                        drafts.get(levelId).goalRegions = regions;
                    } else if (drafts.size() == 1) {
                        LevelDraft single = drafts.values().iterator().next();
                        single.goalMarkerId = markerId;
                        single.goalRegions = regions;
                    }
                }
            }
        }

        // 4. Parse lobbySpawns
        List<MapPosition> lobby = new ArrayList<>();
        if (json.has("lobbySpawns") && json.get("lobbySpawns").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("lobbySpawns")) {
                if (el.isJsonObject()) {
                    MapPosition p = extractPoint(el.getAsJsonObject());
                    if (p != null) {
                        lobby.add(p);
                    }
                }
            }
        }

        List<DropperLevel> completeLevels = new ArrayList<>();
        for (LevelDraft draft : drafts.values()) {
            if (draft.spawn != null && !draft.goalRegions.isEmpty()) {
                completeLevels.add(new DropperLevel(draft.id, draft.name, draft.spawn, draft.goalMarkerId, draft.goalRegions));
            }
        }

        return new DropperMapConfig(completeLevels, lobby);
    }

    public static DropperMapConfig fromJsonString(String value) {
        try {
            JsonElement element = JsonParser.parseString(value == null ? "{}" : value);
            return element.isJsonObject() ? fromJson(element.getAsJsonObject()) : new DropperMapConfig(List.of(), List.of());
        } catch (Exception ignored) {
            return new DropperMapConfig(List.of(), List.of());
        }
    }

    public static MapValidationResult validate(MapDescriptor map, JsonObject config) {
        return validateEditor(map, config, null);
    }

    public static MapValidationResult validateEditor(MapDescriptor map, JsonObject config, dev.frost.miniverse.map.editor.MapEditorExtension extension) {
        MapValidationResult.Builder builder = MapValidationResult.builder();
        if (config == null || !config.has("levels") || !config.get("levels").isJsonArray() || config.getAsJsonArray("levels").isEmpty()) {
            builder.error("At least one Dropper Level must be defined.");
        }

        DropperMapConfig parsed = fromJson(config);
        if (config != null && config.has("levels") && config.get("levels").isJsonArray()) {
            for (JsonElement el : config.getAsJsonArray("levels")) {
                if (el.isJsonObject()) {
                    String id = el.getAsJsonObject().has("id") ? el.getAsJsonObject().get("id").getAsString() : "";
                    String name = el.getAsJsonObject().has("name") ? el.getAsJsonObject().get("name").getAsString() : "Level";
                    boolean complete = parsed.levels().stream().anyMatch(l -> l.id().equals(id));
                    if (!complete) {
                        builder.error("Level '" + name + "' is missing its Level Spawn or Level Goal region.");
                    }
                }
            }
        }

        return builder.build();
    }

    private static String extractLevelId(JsonObject obj) {
        if (obj.has("properties") && obj.get("properties").isJsonObject()) {
            JsonObject props = obj.getAsJsonObject("properties");
            if (props.has("levelId")) {
                return props.get("levelId").getAsString();
            }
        }
        return "";
    }

    private static MapPosition extractPoint(JsonObject obj) {
        if (obj.has("points") && obj.get("points").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("points");
            if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                return MapPosition.fromJson(arr.get(0).getAsJsonObject(), MapPosition.of(0, 100, 0));
            }
        }
        if (obj.has("x") && obj.has("y") && obj.has("z")) {
            return MapPosition.fromJson(obj, MapPosition.of(0, 100, 0));
        }
        return null;
    }

    private static List<RegionPart> extractRegions(JsonObject obj) {
        List<RegionPart> regions = new ArrayList<>();
        if (obj.has("regions") && obj.get("regions").isJsonArray()) {
            for (JsonElement r : obj.getAsJsonArray("regions")) {
                if (r.isJsonObject()) {
                    regions.add(RegionPart.fromJson(r.getAsJsonObject()));
                }
            }
        }
        return regions;
    }
}
