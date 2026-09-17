package dev.frost.miniverse.minigame.impl.zombies.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.map.MapDescriptor;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.MapEditorMarkerStore;
import dev.frost.miniverse.map.editor.RegionPart;
import dev.frost.miniverse.minigame.impl.zombies.ZombiesDefinition;
import dev.frost.miniverse.minigame.impl.zombies.perk.PlayerPerk;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponType;
import net.minecraft.util.math.BlockPos;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public record ZombiesMapConfig(
    String startArea,
    List<BlockPos> playerSpawns,
    List<String> areas,
    List<ZombiesDoor> doors,
    List<ZombiesWindow> windows,
    List<ZombiesWeaponShop> weaponShops,
    List<ZombiesArmorShop> armorShops,
    List<ZombiesPerkMachine> perkMachines,
    List<ZombiesLuckyChest> luckyChests,
    ZombiesPowerSwitch powerSwitch,
    ZombiesTeamMachine teamMachine
) {
    public static MapValidationResult validateEditor(MapDescriptor map, JsonObject config) {
        if (config != null && config.has("startArea") && config.has("doors")) {
            return MapValidationResult.ok();
        }
        return MapEditorMarkerStore.validate(map, config, ZombiesDefinition.EXTENSION);
    }

    public static ZombiesMapConfig fromJsonString(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank() || "{}".equals(jsonStr.trim())) {
            return loadDefaultTemplate();
        }
        try {
            JsonElement element = JsonParser.parseString(jsonStr);
            if (element.isJsonObject()) {
                return fromJson(element.getAsJsonObject());
            }
        } catch (Exception e) {
            Miniverse.LOGGER.warn("ZombiesMapConfig: failed to parse map config JSON, falling back to default template", e);
        }
        return loadDefaultTemplate();
    }

    public static ZombiesMapConfig empty() {
        return new ZombiesMapConfig("Alley", List.of(), List.of("Alley"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
    }

    public static ZombiesMapConfig fromJson(JsonObject json) {
        if (json == null || json.isEmpty()) {
            return loadDefaultTemplate();
        }

        // If it's a legacy dead_end.json template format:
        if (json.has("startArea") && json.has("doors") && !json.has("areaConfigs")) {
            return fromLegacyTemplateJson(json);
        }

        String startArea = "Alley";
        List<String> areas = new ArrayList<>();
        List<BlockPos> spawns = new ArrayList<>();
        List<ZombiesDoor> doors = new ArrayList<>();
        List<ZombiesWindow> windows = new ArrayList<>();
        List<ZombiesWeaponShop> weaponShops = new ArrayList<>();
        List<ZombiesArmorShop> armorShops = new ArrayList<>();
        List<ZombiesPerkMachine> perkMachines = new ArrayList<>();
        List<ZombiesLuckyChest> luckyChests = new ArrayList<>();
        ZombiesPowerSwitch powerSwitch = null;
        ZombiesTeamMachine teamMachine = null;

        // 1. Area configs
        if (json.has("areaConfigs") && json.get("areaConfigs").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("areaConfigs")) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String areaName = obj.has("name") ? obj.get("name").getAsString() : "";
                if (!areaName.isBlank()) {
                    areas.add(areaName);
                }
                if (obj.has("properties")) {
                    JsonObject props = obj.getAsJsonObject("properties");
                    if (props.has("isStartArea") && props.get("isStartArea").getAsBoolean()) {
                        startArea = areaName;
                    }
                }
            }
        }
        if (areas.isEmpty()) areas.add(startArea);

        // 2. Player spawns
        if (json.has("playerSpawns") && json.get("playerSpawns").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("playerSpawns")) {
                if (!el.isJsonObject()) continue;
                MapPosition pos = extractPosition(el.getAsJsonObject());
                if (pos != null) {
                    spawns.add(BlockPos.ofFloored(pos.x(), pos.y(), pos.z()));
                }
            }
        }

        // 3. Doors
        if (json.has("doors") && json.get("doors").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("doors")) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : "";
                String area1 = "";
                String area2 = "";
                int gold = 750;
                if (obj.has("properties")) {
                    JsonObject props = obj.getAsJsonObject("properties");
                    area1 = props.has("area1") ? props.get("area1").getAsString() : "";
                    area2 = props.has("area2") ? props.get("area2").getAsString() : "";
                    gold = props.has("gold") ? props.get("gold").getAsInt() : 750;
                }
                RegionPart bounds = extractFirstRegion(obj);
                RegionPart templateClosed = null;

                // Check for linked closed template
                if (json.has("doorClosedTemplates") && json.get("doorClosedTemplates").isJsonArray()) {
                    for (JsonElement tEl : json.getAsJsonArray("doorClosedTemplates")) {
                        if (!tEl.isJsonObject()) continue;
                        JsonObject tObj = tEl.getAsJsonObject();
                        if (tObj.has("properties")) {
                            JsonObject tProps = tObj.getAsJsonObject("properties");
                            if (tProps.has("doorId") && tProps.get("doorId").getAsString().equals(id)) {
                                templateClosed = extractFirstRegion(tObj);
                                break;
                            }
                        }
                    }
                }
                doors.add(new ZombiesDoor(id, area1, area2, gold, bounds, templateClosed));
            }
        }

        // 4. Windows
        if (json.has("windowSpawns") && json.get("windowSpawns").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("windowSpawns")) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : "";
                String area = "";
                if (obj.has("properties")) {
                    JsonObject props = obj.getAsJsonObject("properties");
                    area = props.has("area") ? props.get("area").getAsString() : startArea;
                }
                MapPosition spawnPos = extractPosition(obj);
                RegionPart blockBounds = null;
                RegionPart repairZone = null;

                if (json.has("windowBlocks") && json.get("windowBlocks").isJsonArray()) {
                    for (JsonElement bEl : json.getAsJsonArray("windowBlocks")) {
                        if (bEl.isJsonObject() && matchesWindowId(bEl.getAsJsonObject(), id)) {
                            blockBounds = extractFirstRegion(bEl.getAsJsonObject());
                            break;
                        }
                    }
                }
                if (json.has("windowRepairZones") && json.get("windowRepairZones").isJsonArray()) {
                    for (JsonElement rEl : json.getAsJsonArray("windowRepairZones")) {
                        if (rEl.isJsonObject() && matchesWindowId(rEl.getAsJsonObject(), id)) {
                            repairZone = extractFirstRegion(rEl.getAsJsonObject());
                            break;
                        }
                    }
                }
                windows.add(new ZombiesWindow(id, area, spawnPos, blockBounds, repairZone));
            }
        }

        // 5. Weapon Shops
        if (json.has("weaponShops") && json.get("weaponShops").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("weaponShops")) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                MapPosition pos = extractPosition(obj);
                if (pos == null) continue;
                String weaponStr = "PISTOL";
                int buyPrice = 500;
                int refillPrice = 200;
                if (obj.has("properties")) {
                    JsonObject props = obj.getAsJsonObject("properties");
                    weaponStr = props.has("weapon") ? props.get("weapon").getAsString() : "PISTOL";
                    buyPrice = props.has("purchasePrice") ? props.get("purchasePrice").getAsInt() : 500;
                    refillPrice = props.has("refillPrice") ? props.get("refillPrice").getAsInt() : 200;
                }
                weaponShops.add(new ZombiesWeaponShop(obj.has("id") ? obj.get("id").getAsString() : "", BlockPos.ofFloored(pos.x(), pos.y(), pos.z()), WeaponType.fromString(weaponStr), buyPrice, refillPrice));
            }
        }

        // 6. Armor Shops
        if (json.has("armorShops") && json.get("armorShops").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("armorShops")) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                MapPosition pos = extractPosition(obj);
                if (pos == null) continue;
                String partStr = "UPPER_BODY";
                String qualStr = "LEATHER";
                int price = 50;
                if (obj.has("properties")) {
                    JsonObject props = obj.getAsJsonObject("properties");
                    partStr = props.has("part") ? props.get("part").getAsString() : "UPPER_BODY";
                    qualStr = props.has("quality") ? props.get("quality").getAsString() : "LEATHER";
                    price = props.has("price") ? props.get("price").getAsInt() : 50;
                }
                ZombiesArmorShop.ArmorPart part = "LOWER_BODY".equalsIgnoreCase(partStr) ? ZombiesArmorShop.ArmorPart.LOWER_BODY : ZombiesArmorShop.ArmorPart.UPPER_BODY;
                ZombiesArmorShop.ArmorQuality qual;
                try {
                    qual = ZombiesArmorShop.ArmorQuality.valueOf(qualStr.toUpperCase());
                } catch (Exception ignored) {
                    qual = ZombiesArmorShop.ArmorQuality.LEATHER;
                }
                armorShops.add(new ZombiesArmorShop(obj.has("id") ? obj.get("id").getAsString() : "", BlockPos.ofFloored(pos.x(), pos.y(), pos.z()), part, qual, price));
            }
        }

        // 7. Perk Machines
        if (json.has("perkMachines") && json.get("perkMachines").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("perkMachines")) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                MapPosition pos = extractPosition(obj);
                if (pos == null) continue;
                String perkStr = "SPEED";
                int gold = 500;
                if (obj.has("properties")) {
                    JsonObject props = obj.getAsJsonObject("properties");
                    perkStr = props.has("perk") ? props.get("perk").getAsString() : "SPEED";
                    gold = props.has("gold") ? props.get("gold").getAsInt() : 500;
                }
                perkMachines.add(new ZombiesPerkMachine(obj.has("id") ? obj.get("id").getAsString() : "", BlockPos.ofFloored(pos.x(), pos.y(), pos.z()), PlayerPerk.fromString(perkStr), gold));
            }
        }

        // 8. Lucky Chests
        if (json.has("luckyChests") && json.get("luckyChests").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("luckyChests")) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                MapPosition pos = extractPosition(obj);
                if (pos == null) continue;
                int gold = 1000;
                if (obj.has("properties")) {
                    JsonObject props = obj.getAsJsonObject("properties");
                    gold = props.has("gold") ? props.get("gold").getAsInt() : 1000;
                }
                luckyChests.add(new ZombiesLuckyChest(obj.has("id") ? obj.get("id").getAsString() : "", BlockPos.ofFloored(pos.x(), pos.y(), pos.z()), gold));
            }
        }

        // 9. Power Switch
        if (json.has("powerSwitches") && json.get("powerSwitches").isJsonArray() && !json.getAsJsonArray("powerSwitches").isEmpty()) {
            JsonObject obj = json.getAsJsonArray("powerSwitches").get(0).getAsJsonObject();
            MapPosition pos = extractPosition(obj);
            if (pos != null) {
                int gold = 1000;
                if (obj.has("properties")) {
                    JsonObject props = obj.getAsJsonObject("properties");
                    gold = props.has("gold") ? props.get("gold").getAsInt() : 1000;
                }
                powerSwitch = new ZombiesPowerSwitch(obj.has("id") ? obj.get("id").getAsString() : "", BlockPos.ofFloored(pos.x(), pos.y(), pos.z()), gold);
            }
        }

        // 10. Team Machine
        if (json.has("teamMachines") && json.get("teamMachines").isJsonArray() && !json.getAsJsonArray("teamMachines").isEmpty()) {
            JsonObject obj = json.getAsJsonArray("teamMachines").get(0).getAsJsonObject();
            MapPosition pos = extractPosition(obj);
            if (pos != null) {
                teamMachine = new ZombiesTeamMachine(obj.has("id") ? obj.get("id").getAsString() : "", BlockPos.ofFloored(pos.x(), pos.y(), pos.z()));
            }
        }

        return new ZombiesMapConfig(startArea, spawns, areas, doors, windows, weaponShops, armorShops, perkMachines, luckyChests, powerSwitch, teamMachine);
    }

    public static ZombiesMapConfig loadDefaultTemplate() {
        try (InputStream stream = ZombiesMapConfig.class.getResourceAsStream("/templates/dead_end.json")) {
            if (stream == null) {
                Miniverse.LOGGER.error("Zombies: Could not find /templates/dead_end.json in classpath!");
                return empty();
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return fromLegacyTemplateJson(json);
        } catch (Exception e) {
            Miniverse.LOGGER.error("Zombies: Failed to load /templates/dead_end.json", e);
            return empty();
        }
    }

    public static ZombiesMapConfig fromLegacyTemplateJson(JsonObject json) {
        String startArea = json.has("startArea") ? json.get("startArea").getAsString() : "Alley";
        List<String> areas = new ArrayList<>();
        areas.add(startArea);
        List<BlockPos> spawns = new ArrayList<>();
        List<ZombiesDoor> doors = new ArrayList<>();
        List<ZombiesWindow> windows = new ArrayList<>();
        List<ZombiesWeaponShop> weaponShops = new ArrayList<>();
        List<ZombiesArmorShop> armorShops = new ArrayList<>();
        List<ZombiesPerkMachine> perkMachines = new ArrayList<>();
        List<ZombiesLuckyChest> luckyChests = new ArrayList<>();
        ZombiesPowerSwitch powerSwitch = null;
        ZombiesTeamMachine teamMachine = null;

        // Doors
        if (json.has("doors") && json.get("doors").isJsonArray()) {
            int idx = 1;
            for (JsonElement el : json.getAsJsonArray("doors")) {
                if (!el.isJsonObject()) continue;
                JsonObject d = el.getAsJsonObject();
                String area1 = d.has("area1") ? d.get("area1").getAsString() : "";
                String area2 = d.has("area2") ? d.get("area2").getAsString() : "";
                if (!areas.contains(area1)) areas.add(area1);
                if (!areas.contains(area2)) areas.add(area2);
                int gold = d.has("gold") ? d.get("gold").getAsInt() : 750;
                RegionPart bounds = parseLegacyBoundingBox(d, "position");
                RegionPart closedTemplate = parseLegacyBoundingBox(d, "templateClosed");
                doors.add(new ZombiesDoor("door_" + idx++, area1, area2, gold, bounds, closedTemplate));
            }
        }

        // Windows
        if (json.has("windows") && json.get("windows").isJsonArray()) {
            int idx = 1;
            for (JsonElement el : json.getAsJsonArray("windows")) {
                if (!el.isJsonObject()) continue;
                JsonObject w = el.getAsJsonObject();
                String area = w.has("area") ? w.get("area").getAsString() : startArea;
                if (!areas.contains(area)) areas.add(area);
                MapPosition spawnPos = parseLegacySpawnPos(w.getAsJsonObject("spawnLocation"));
                RegionPart blocks = parseLegacyBoundingBox(w, "blocks");
                RegionPart repair = parseLegacyBoundingBox(w, "repairArea");
                windows.add(new ZombiesWindow("window_" + idx++, area, spawnPos, blocks, repair));
            }
        }

        // Weapon Shops
        if (json.has("weaponShops") && json.get("weaponShops").isJsonArray()) {
            int idx = 1;
            for (JsonElement el : json.getAsJsonArray("weaponShops")) {
                if (!el.isJsonObject()) continue;
                JsonObject ws = el.getAsJsonObject();
                BlockPos pos = parseLegacyPos(ws.getAsJsonObject("position"));
                String weaponStr = ws.has("weaponType") ? ws.get("weaponType").getAsString() : "PISTOL";
                int buyPrice = ws.has("purchasePrice") ? ws.get("purchasePrice").getAsInt() : 500;
                int refillPrice = ws.has("refillPrice") ? ws.get("refillPrice").getAsInt() : 200;
                weaponShops.add(new ZombiesWeaponShop("weapon_shop_" + idx++, pos, WeaponType.fromString(weaponStr), buyPrice, refillPrice));
            }
        }

        // Armor Shops
        if (json.has("armorShops") && json.get("armorShops").isJsonArray()) {
            int idx = 1;
            for (JsonElement el : json.getAsJsonArray("armorShops")) {
                if (!el.isJsonObject()) continue;
                JsonObject as = el.getAsJsonObject();
                BlockPos pos = parseLegacyPos(as.getAsJsonObject("position"));
                String partStr = as.has("part") ? as.get("part").getAsString() : "UPPER_BODY";
                String qualStr = as.has("quality") ? as.get("quality").getAsString() : "LEATHER";
                int price = as.has("price") ? as.get("price").getAsInt() : 50;
                ZombiesArmorShop.ArmorPart part = "LOWER_BODY".equalsIgnoreCase(partStr) ? ZombiesArmorShop.ArmorPart.LOWER_BODY : ZombiesArmorShop.ArmorPart.UPPER_BODY;
                ZombiesArmorShop.ArmorQuality qual;
                try {
                    qual = ZombiesArmorShop.ArmorQuality.valueOf(qualStr.toUpperCase());
                } catch (Exception ignored) {
                    qual = ZombiesArmorShop.ArmorQuality.LEATHER;
                }
                armorShops.add(new ZombiesArmorShop("armor_shop_" + idx++, pos, part, qual, price));
            }
        }

        // Lucky Chests
        if (json.has("luckyChests") && json.get("luckyChests").isJsonArray()) {
            int idx = 1;
            for (JsonElement el : json.getAsJsonArray("luckyChests")) {
                if (!el.isJsonObject()) continue;
                JsonObject lc = el.getAsJsonObject();
                BlockPos pos = parseLegacyPos(lc.getAsJsonObject("position"));
                int gold = lc.has("gold") ? lc.get("gold").getAsInt() : 1000;
                luckyChests.add(new ZombiesLuckyChest("lucky_chest_" + idx++, pos, gold));
            }
        }

        // Perk Machines
        if (json.has("perkMachines") && json.get("perkMachines").isJsonArray()) {
            int idx = 1;
            for (JsonElement el : json.getAsJsonArray("perkMachines")) {
                if (!el.isJsonObject()) continue;
                JsonObject pm = el.getAsJsonObject();
                BlockPos pos = parseLegacyPos(pm.getAsJsonObject("position"));
                String perkStr = pm.has("perk") ? pm.get("perk").getAsString() : "SPEED";
                int gold = pm.has("gold") ? pm.get("gold").getAsInt() : 500;
                perkMachines.add(new ZombiesPerkMachine("perk_" + idx++, pos, PlayerPerk.fromString(perkStr), gold));
            }
        }

        // Power Switch
        if (json.has("powerSwitch") && json.get("powerSwitch").isJsonObject()) {
            JsonObject ps = json.getAsJsonObject("powerSwitch");
            BlockPos pos = parseLegacyPos(ps.getAsJsonObject("position"));
            int gold = ps.has("gold") ? ps.get("gold").getAsInt() : 1000;
            powerSwitch = new ZombiesPowerSwitch("power_switch", pos, gold);
        }

        // Team Machine
        if (json.has("teamMachine") && json.get("teamMachine").isJsonObject()) {
            BlockPos pos = parseLegacyPos(json.getAsJsonObject("teamMachine"));
            teamMachine = new ZombiesTeamMachine("team_machine", pos);
        }

        // Player default spawn in Alley (default from Dead End)
        spawns.add(new BlockPos(17, 69, 30));

        return new ZombiesMapConfig(startArea, spawns, areas, doors, windows, weaponShops, armorShops, perkMachines, luckyChests, powerSwitch, teamMachine);
    }

    private static boolean matchesWindowId(JsonObject obj, String id) {
        if (obj.has("properties")) {
            JsonObject props = obj.getAsJsonObject("properties");
            return props.has("windowId") && props.get("windowId").getAsString().equals(id);
        }
        return false;
    }

    private static MapPosition extractPosition(JsonObject markerObj) {
        if (markerObj.has("points") && markerObj.get("points").isJsonArray()) {
            JsonArray pts = markerObj.getAsJsonArray("points");
            if (!pts.isEmpty() && pts.get(0).isJsonObject()) {
                return MapPosition.fromJson(pts.get(0).getAsJsonObject(), MapPosition.of(0, 70, 0));
            }
        }
        if (markerObj.has("position") && markerObj.get("position").isJsonObject()) {
            return MapPosition.fromJson(markerObj.getAsJsonObject("position"), MapPosition.of(0, 70, 0));
        }
        return null;
    }

    private static RegionPart extractFirstRegion(JsonObject markerObj) {
        if (markerObj.has("regions") && markerObj.get("regions").isJsonArray()) {
            JsonArray regs = markerObj.getAsJsonArray("regions");
            if (!regs.isEmpty() && regs.get(0).isJsonObject()) {
                return RegionPart.fromJson(regs.get(0).getAsJsonObject());
            }
        }
        return null;
    }

    private static RegionPart parseLegacyBoundingBox(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) return null;
        JsonObject box = parent.getAsJsonObject(key);
        if (!box.has("position1") || !box.has("position2")) return null;
        JsonObject p1 = box.getAsJsonObject("position1");
        JsonObject p2 = box.getAsJsonObject("position2");
        double x1 = p1.get("x").getAsDouble();
        double y1 = p1.get("y").getAsDouble();
        double z1 = p1.get("z").getAsDouble();
        double x2 = p2.get("x").getAsDouble();
        double y2 = p2.get("y").getAsDouble();
        double z2 = p2.get("z").getAsDouble();
        MapPosition min = MapPosition.of(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
        MapPosition max = MapPosition.of(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        return new RegionPart(min, max);
    }

    private static BlockPos parseLegacyPos(JsonObject obj) {
        if (obj == null) return BlockPos.ORIGIN;
        int x = obj.has("x") ? obj.get("x").getAsInt() : 0;
        int y = obj.has("y") ? obj.get("y").getAsInt() : 70;
        int z = obj.has("z") ? obj.get("z").getAsInt() : 0;
        return new BlockPos(x, y, z);
    }

    private static MapPosition parseLegacySpawnPos(JsonObject obj) {
        if (obj == null) return MapPosition.of(0, 70, 0);
        double x = obj.has("x") ? obj.get("x").getAsDouble() : 0;
        double y = obj.has("y") ? obj.get("y").getAsDouble() : 70;
        double z = obj.has("z") ? obj.get("z").getAsDouble() : 0;
        float yaw = obj.has("yaw") ? obj.get("yaw").getAsFloat() : 0;
        float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 0;
        return new MapPosition(x, y, z, yaw, pitch);
    }

    public JsonObject toMarkerJson() {
        JsonObject root = new JsonObject();

        // 1. playerSpawns
        JsonArray spawnsArr = new JsonArray();
        for (BlockPos p : this.playerSpawns) {
            JsonObject pt = new JsonObject();
            pt.addProperty("name", "Survivor Spawn");
            pt.add("points", makeSinglePointArray(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0));
            spawnsArr.add(pt);
        }
        root.add("playerSpawns", spawnsArr);

        // 2. areaConfigs
        JsonArray areasArr = new JsonArray();
        for (String area : this.areas) {
            JsonObject a = new JsonObject();
            a.addProperty("name", area);
            JsonObject props = new JsonObject();
            props.addProperty("areaName", area);
            props.addProperty("isStartArea", area.equalsIgnoreCase(this.startArea));
            a.add("properties", props);
            a.add("points", makeSinglePointArray(17, 69, 30, 0, 0));
            areasArr.add(a);
        }
        root.add("areaConfigs", areasArr);

        // 3. doors
        JsonArray doorsArr = new JsonArray();
        JsonArray doorClosedArr = new JsonArray();
        for (ZombiesDoor door : this.doors) {
            JsonObject d = new JsonObject();
            d.addProperty("id", door.getId());
            d.addProperty("name", door.getId());
            JsonObject props = new JsonObject();
            props.addProperty("area1", door.getArea1());
            props.addProperty("area2", door.getArea2());
            props.addProperty("gold", door.getGold());
            d.add("properties", props);
            if (door.getBounds() != null) {
                d.add("regions", makeSingleRegionArray(door.getBounds()));
            }
            doorsArr.add(d);

            if (door.getTemplateClosed() != null) {
                JsonObject dc = new JsonObject();
                dc.addProperty("id", door.getId() + "_closed");
                dc.addProperty("name", door.getId() + " Closed");
                JsonObject dcProps = new JsonObject();
                dcProps.addProperty("doorId", door.getId());
                dc.add("properties", dcProps);
                dc.add("regions", makeSingleRegionArray(door.getTemplateClosed()));
                doorClosedArr.add(dc);
            }
        }
        root.add("doors", doorsArr);
        root.add("doorClosedTemplates", doorClosedArr);

        // 4. windows
        JsonArray winSpawnsArr = new JsonArray();
        JsonArray winBlocksArr = new JsonArray();
        JsonArray winRepairsArr = new JsonArray();
        for (ZombiesWindow w : this.windows) {
            JsonObject ws = new JsonObject();
            ws.addProperty("id", w.getId());
            ws.addProperty("name", w.getId() + " Spawn");
            JsonObject wsProps = new JsonObject();
            wsProps.addProperty("windowId", w.getId());
            wsProps.addProperty("area", w.getArea());
            ws.add("properties", wsProps);
            ws.add("points", makeSinglePointArray(w.getSpawnPos().x(), w.getSpawnPos().y(), w.getSpawnPos().z(), w.getSpawnPos().yaw(), w.getSpawnPos().pitch()));
            winSpawnsArr.add(ws);

            if (w.getBlockBounds() != null) {
                JsonObject wb = new JsonObject();
                wb.addProperty("id", w.getId() + "_blocks");
                wb.addProperty("name", w.getId() + " Blocks");
                JsonObject wbProps = new JsonObject();
                wbProps.addProperty("windowId", w.getId());
                wb.add("properties", wbProps);
                wb.add("regions", makeSingleRegionArray(w.getBlockBounds()));
                winBlocksArr.add(wb);
            }

            if (w.getRepairZone() != null) {
                JsonObject wr = new JsonObject();
                wr.addProperty("id", w.getId() + "_repair");
                wr.addProperty("name", w.getId() + " Repair");
                JsonObject wrProps = new JsonObject();
                wrProps.addProperty("windowId", w.getId());
                wr.add("properties", wrProps);
                wr.add("regions", makeSingleRegionArray(w.getRepairZone()));
                winRepairsArr.add(wr);
            }
        }
        root.add("windowSpawns", winSpawnsArr);
        root.add("windowBlocks", winBlocksArr);
        root.add("windowRepairZones", winRepairsArr);

        // 5. weaponShops
        JsonArray weaponsArr = new JsonArray();
        for (ZombiesWeaponShop ws : this.weaponShops) {
            JsonObject o = new JsonObject();
            o.addProperty("id", ws.getId());
            o.addProperty("name", ws.getWeaponType().getData().displayName() + " Shop");
            JsonObject props = new JsonObject();
            props.addProperty("weapon", ws.getWeaponType().name());
            props.addProperty("purchasePrice", ws.getPurchasePrice());
            props.addProperty("refillPrice", ws.getRefillPrice());
            o.add("properties", props);
            o.add("points", makeSinglePointArray(ws.getPos().getX(), ws.getPos().getY(), ws.getPos().getZ(), 0, 0));
            weaponsArr.add(o);
        }
        root.add("weaponShops", weaponsArr);

        // 6. armorShops
        JsonArray armorsArr = new JsonArray();
        for (ZombiesArmorShop as : this.armorShops) {
            JsonObject o = new JsonObject();
            o.addProperty("id", as.getId());
            o.addProperty("name", as.getQuality().name() + " " + as.getPart().name() + " Armor");
            JsonObject props = new JsonObject();
            props.addProperty("part", as.getPart().name());
            props.addProperty("quality", as.getQuality().name());
            props.addProperty("price", as.getPrice());
            o.add("properties", props);
            o.add("points", makeSinglePointArray(as.getPos().getX(), as.getPos().getY(), as.getPos().getZ(), 0, 0));
            armorsArr.add(o);
        }
        root.add("armorShops", armorsArr);

        // 7. perkMachines
        JsonArray perksArr = new JsonArray();
        for (ZombiesPerkMachine pm : this.perkMachines) {
            JsonObject o = new JsonObject();
            o.addProperty("id", pm.getId());
            o.addProperty("name", pm.getPerk().getDisplayName() + " Perk");
            JsonObject props = new JsonObject();
            props.addProperty("perk", pm.getPerk().name());
            props.addProperty("gold", pm.getGold());
            o.add("properties", props);
            o.add("points", makeSinglePointArray(pm.getPos().getX(), pm.getPos().getY(), pm.getPos().getZ(), 0, 0));
            perksArr.add(o);
        }
        root.add("perkMachines", perksArr);

        // 8. luckyChests
        JsonArray chestsArr = new JsonArray();
        for (ZombiesLuckyChest lc : this.luckyChests) {
            JsonObject o = new JsonObject();
            o.addProperty("id", lc.getId());
            o.addProperty("name", "Lucky Chest");
            JsonObject props = new JsonObject();
            props.addProperty("gold", lc.getGold());
            o.add("properties", props);
            o.add("points", makeSinglePointArray(lc.getPos().getX(), lc.getPos().getY(), lc.getPos().getZ(), 0, 0));
            chestsArr.add(o);
        }
        root.add("luckyChests", chestsArr);

        // 9. powerSwitches
        JsonArray powerArr = new JsonArray();
        if (this.powerSwitch != null) {
            JsonObject o = new JsonObject();
            o.addProperty("id", this.powerSwitch.getId());
            o.addProperty("name", "Power Switch");
            JsonObject props = new JsonObject();
            props.addProperty("gold", this.powerSwitch.getGold());
            o.add("properties", props);
            o.add("points", makeSinglePointArray(this.powerSwitch.getPos().getX(), this.powerSwitch.getPos().getY(), this.powerSwitch.getPos().getZ(), 0, 0));
            powerArr.add(o);
        }
        root.add("powerSwitches", powerArr);

        // 10. teamMachines
        JsonArray teamArr = new JsonArray();
        if (this.teamMachine != null) {
            JsonObject o = new JsonObject();
            o.addProperty("id", this.teamMachine.getId());
            o.addProperty("name", "Team Machine");
            o.add("properties", new JsonObject());
            o.add("points", makeSinglePointArray(this.teamMachine.getPos().getX(), this.teamMachine.getPos().getY(), this.teamMachine.getPos().getZ(), 0, 0));
            teamArr.add(o);
        }
        root.add("teamMachines", teamArr);

        return root;
    }

    private static JsonArray makeSinglePointArray(double x, double y, double z, float yaw, float pitch) {
        JsonArray arr = new JsonArray();
        JsonObject obj = new JsonObject();
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        if (yaw != 0) obj.addProperty("yaw", yaw);
        if (pitch != 0) obj.addProperty("pitch", pitch);
        arr.add(obj);
        return arr;
    }

    private static JsonArray makeSingleRegionArray(RegionPart region) {
        JsonArray arr = new JsonArray();
        arr.add(region.toJson());
        return arr;
    }

    public BlockPos getEffectiveUltimateMachinePos() {
        // In Dead End map, the Ultimate Machine is located at the stone button in the Garden
        return new BlockPos(16, 68, -32);
    }
}
