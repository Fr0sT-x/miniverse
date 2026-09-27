package dev.frost.miniverse.minigame.impl.microfrenzy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.map.MapDescriptor;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.RegionPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record MicroFrenzyMapConfig(
    List<RegionPart> arenaBounds,
    MapPosition arenaCenter,
    List<MapPosition> playerSpawns,
    List<MapPosition> lobbySpawns,
    List<ColorZone> colorZones,
    List<RegionPart> highGround,
    List<MapPosition> targets
) {
    public MicroFrenzyMapConfig {
        arenaBounds = arenaBounds == null ? List.of() : List.copyOf(arenaBounds);
        playerSpawns = playerSpawns == null ? List.of() : List.copyOf(playerSpawns);
        lobbySpawns = lobbySpawns == null ? List.of() : List.copyOf(lobbySpawns);
        colorZones = colorZones == null ? List.of() : List.copyOf(colorZones);
        highGround = highGround == null ? List.of() : List.copyOf(highGround);
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    public record ColorZone(
        String markerId,
        String color,
        List<RegionPart> regions
    ) {
        public ColorZone {
            regions = regions == null ? List.of() : List.copyOf(regions);
        }
    }

    public static MicroFrenzyMapConfig fromJson(JsonObject json) {
        if (json == null) {
            return new MicroFrenzyMapConfig(List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // 1. arenaBounds
        List<RegionPart> arenaBounds = new ArrayList<>();
        if (json.has("arenaBounds")) {
            JsonElement el = json.get("arenaBounds");
            if (el.isJsonArray()) {
                for (JsonElement child : el.getAsJsonArray()) {
                    if (child.isJsonObject()) {
                        arenaBounds.addAll(extractRegions(child.getAsJsonObject()));
                    }
                }
            } else if (el.isJsonObject()) {
                arenaBounds.addAll(extractRegions(el.getAsJsonObject()));
            }
        }

        // 2. arenaCenter
        MapPosition arenaCenter = null;
        if (json.has("arenaCenter")) {
            JsonElement el = json.get("arenaCenter");
            if (el.isJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                    arenaCenter = extractPoint(arr.get(0).getAsJsonObject());
                }
            } else if (el.isJsonObject()) {
                arenaCenter = extractPoint(el.getAsJsonObject());
            }
        }

        // 3. playerSpawns
        List<MapPosition> playerSpawns = new ArrayList<>();
        if (json.has("playerSpawns")) {
            JsonElement el = json.get("playerSpawns");
            if (el.isJsonArray()) {
                for (JsonElement child : el.getAsJsonArray()) {
                    if (child.isJsonObject()) {
                        MapPosition p = extractPoint(child.getAsJsonObject());
                        if (p != null) {
                            playerSpawns.add(p);
                        }
                    }
                }
            } else if (el.isJsonObject()) {
                MapPosition p = extractPoint(el.getAsJsonObject());
                if (p != null) {
                    playerSpawns.add(p);
                }
            }
        }

        // 4. lobbySpawns
        List<MapPosition> lobbySpawns = new ArrayList<>();
        if (json.has("lobbySpawns")) {
            JsonElement el = json.get("lobbySpawns");
            if (el.isJsonArray()) {
                for (JsonElement child : el.getAsJsonArray()) {
                    if (child.isJsonObject()) {
                        MapPosition p = extractPoint(child.getAsJsonObject());
                        if (p != null) {
                            lobbySpawns.add(p);
                        }
                    }
                }
            } else if (el.isJsonObject()) {
                MapPosition p = extractPoint(el.getAsJsonObject());
                if (p != null) {
                    lobbySpawns.add(p);
                }
            }
        }

        // 5. colorZones
        List<ColorZone> colorZones = new ArrayList<>();
        if (json.has("colorZones")) {
            JsonElement el = json.get("colorZones");
            if (el.isJsonArray()) {
                for (JsonElement child : el.getAsJsonArray()) {
                    if (child.isJsonObject()) {
                        colorZones.add(parseColorZone(child.getAsJsonObject()));
                    }
                }
            } else if (el.isJsonObject()) {
                colorZones.add(parseColorZone(el.getAsJsonObject()));
            }
        }

        // 6. highGround
        List<RegionPart> highGround = new ArrayList<>();
        if (json.has("highGround")) {
            JsonElement el = json.get("highGround");
            if (el.isJsonArray()) {
                for (JsonElement child : el.getAsJsonArray()) {
                    if (child.isJsonObject()) {
                        highGround.addAll(extractRegions(child.getAsJsonObject()));
                    }
                }
            } else if (el.isJsonObject()) {
                highGround.addAll(extractRegions(el.getAsJsonObject()));
            }
        }

        // 7. targets
        List<MapPosition> targets = new ArrayList<>();
        if (json.has("targets")) {
            JsonElement el = json.get("targets");
            if (el.isJsonArray()) {
                for (JsonElement child : el.getAsJsonArray()) {
                    if (child.isJsonObject()) {
                        MapPosition p = extractPoint(child.getAsJsonObject());
                        if (p != null) {
                            targets.add(p);
                        }
                    }
                }
            } else if (el.isJsonObject()) {
                MapPosition p = extractPoint(el.getAsJsonObject());
                if (p != null) {
                    targets.add(p);
                }
            }
        }

        // Fallback for arenaCenter if missing: compute from arenaBounds or playerSpawns
        if (arenaCenter == null && !arenaBounds.isEmpty()) {
            RegionPart first = arenaBounds.get(0);
            double midX = (first.min().x() + first.max().x()) / 2.0;
            double midZ = (first.min().z() + first.max().z()) / 2.0;
            double y = !playerSpawns.isEmpty() ? playerSpawns.get(0).y() : Math.min(first.min().y(), first.max().y()) + 1.0;
            arenaCenter = new MapPosition(midX, y, midZ, 0.0F, 0.0F);
        } else if (arenaCenter == null && !playerSpawns.isEmpty()) {
            arenaCenter = playerSpawns.get(0);
        } else if (arenaCenter == null) {
            arenaCenter = MapPosition.of(0, 100, 0);
        }

        return new MicroFrenzyMapConfig(
            arenaBounds, arenaCenter, playerSpawns, lobbySpawns, colorZones, highGround, targets
        );
    }

    public static MicroFrenzyMapConfig fromJsonString(String value) {
        try {
            JsonElement element = JsonParser.parseString(value == null ? "{}" : value);
            return element.isJsonObject() ? fromJson(element.getAsJsonObject()) : new MicroFrenzyMapConfig(List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of());
        } catch (Exception ignored) {
            return new MicroFrenzyMapConfig(List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    public static MapValidationResult validate(MapDescriptor map, JsonObject config) {
        return validateEditor(map, config, null);
    }

    public static MapValidationResult validateEditor(MapDescriptor map, JsonObject config, MapEditorExtension extension) {
        MapValidationResult.Builder builder = MapValidationResult.builder();
        if (config == null) {
            return builder.error("Micro-Frenzy configuration is missing.").build();
        }

        MicroFrenzyMapConfig parsed = fromJson(config);
        if (parsed.arenaBounds().isEmpty()) {
            builder.error("At least one Arena Bounds region must be defined.");
        }
        if (parsed.playerSpawns().size() < 2) {
            builder.error("At least 2 Player Spawn points must be configured.");
        }

        return builder.build();
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

    private static ColorZone parseColorZone(JsonObject obj) {
        String markerId = obj.has("id") ? obj.get("id").getAsString() : "";
        String color = "RED";
        if (obj.has("properties") && obj.get("properties").isJsonObject()) {
            JsonObject props = obj.getAsJsonObject("properties");
            if (props.has("color")) {
                color = props.get("color").getAsString().toUpperCase(Locale.ROOT);
            }
        }
        List<RegionPart> regions = extractRegions(obj);
        return new ColorZone(markerId, color, regions);
    }
}
