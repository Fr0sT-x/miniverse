package dev.frost.miniverse.minigame.impl.zombies;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class ZombiesConfigAndLogicTest {

    @Test
    public void testZombiesSettingsSerialization() {
        ZombiesSettings original = new ZombiesSettings("dead_end", 750, 30, 15, 35, false, ZombiesDifficulty.HARD, true);
        Properties props = new Properties();
        original.writeTo(props);

        ZombiesSettings restored = ZombiesSettings.fromProperties(props);
        Assert.assertEquals("dead_end", restored.mapId());
        Assert.assertEquals(750, restored.startGold());
        Assert.assertEquals(30, restored.maxRounds());
        Assert.assertEquals(15, restored.intermissionSeconds());
        Assert.assertEquals(35, restored.bleedoutSeconds());
        Assert.assertFalse(restored.friendlyFire());
        Assert.assertEquals(ZombiesDifficulty.HARD, restored.difficulty());
        Assert.assertTrue(restored.endlessMode());
    }

    @Test
    public void testZombiesSettingsDefaults() {
        ZombiesSettings defaults = ZombiesSettings.defaults();
        Assert.assertEquals("dead_end", defaults.mapId());
        Assert.assertEquals(500, defaults.startGold());
        Assert.assertEquals(30, defaults.maxRounds());
        Assert.assertEquals(10, defaults.intermissionSeconds());
        Assert.assertEquals(30, defaults.bleedoutSeconds());
        Assert.assertFalse(defaults.friendlyFire());
        Assert.assertEquals(ZombiesDifficulty.EASY, defaults.difficulty());
        Assert.assertFalse(defaults.endlessMode());
    }

    @Test
    public void testBundledTemplateJsonIntegrity() {
        try (InputStream stream = getClass().getResourceAsStream("/templates/dead_end.json")) {
            Assert.assertNotNull("Bundled dead_end.json must be present on classpath", stream);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();

            // 1. Verify player spawns
            Assert.assertTrue("Root must have playerSpawns", root.has("playerSpawns") && root.get("playerSpawns").isJsonArray());
            Assert.assertFalse("Must have at least one player spawn", root.getAsJsonArray("playerSpawns").isEmpty());

            // 2. Verify area configs & start area
            Assert.assertTrue("Root must have areaConfigs", root.has("areaConfigs") && root.get("areaConfigs").isJsonArray());
            JsonArray areas = root.getAsJsonArray("areaConfigs");
            boolean foundStartArea = false;
            for (JsonElement el : areas) {
                JsonObject a = el.getAsJsonObject();
                if (a.has("properties") && a.getAsJsonObject("properties").has("isStartArea")
                        && a.getAsJsonObject("properties").get("isStartArea").getAsBoolean()) {
                    foundStartArea = true;
                    Assert.assertEquals("Alley", a.getAsJsonObject("properties").get("areaName").getAsString());
                }
            }
            Assert.assertTrue("Must have start area configured", foundStartArea);

            // 3. Verify doors
            Assert.assertTrue("Root must have doors", root.has("doors") && root.get("doors").isJsonArray());
            JsonArray doors = root.getAsJsonArray("doors");
            Assert.assertTrue("Must have at least 5 doors", doors.size() >= 5);
            for (JsonElement el : doors) {
                JsonObject d = el.getAsJsonObject();
                Assert.assertTrue("Door must have properties", d.has("properties"));
                JsonObject p = d.getAsJsonObject("properties");
                Assert.assertTrue(p.has("area1"));
                Assert.assertTrue(p.has("area2"));
                Assert.assertTrue(p.has("gold"));
                Assert.assertTrue(d.has("regions"));
            }

            // 4. Verify windows
            Assert.assertTrue("Root must have windowSpawns", root.has("windowSpawns") && root.get("windowSpawns").isJsonArray());
            Assert.assertTrue("Must have at least 10 window spawns", root.getAsJsonArray("windowSpawns").size() >= 10);
            Assert.assertTrue("Root must have windowBlocks", root.has("windowBlocks") && root.get("windowBlocks").isJsonArray());
            Assert.assertTrue("Must have at least 10 window blocks", root.getAsJsonArray("windowBlocks").size() >= 10);

            // 5. Verify shops & machines
            Assert.assertTrue("Must have weaponShops", root.has("weaponShops") && root.get("weaponShops").isJsonArray());
            Assert.assertFalse("Weapon shops must not be empty", root.getAsJsonArray("weaponShops").isEmpty());
            Assert.assertTrue("Must have armorShops", root.has("armorShops") && root.get("armorShops").isJsonArray());
            Assert.assertFalse("Armor shops must not be empty", root.getAsJsonArray("armorShops").isEmpty());
            Assert.assertTrue("Must have perkMachines", root.has("perkMachines") && root.get("perkMachines").isJsonArray());
            Assert.assertFalse("Perk machines must not be empty", root.getAsJsonArray("perkMachines").isEmpty());
            Assert.assertTrue("Must have luckyChests", root.has("luckyChests") && root.get("luckyChests").isJsonArray());
            Assert.assertTrue("Must have powerSwitches", root.has("powerSwitches") && root.get("powerSwitches").isJsonArray());
            Assert.assertTrue("Must have teamMachines", root.has("teamMachines") && root.get("teamMachines").isJsonArray());
            Assert.assertTrue("Must have ultimateMachines", root.has("ultimateMachines") && root.get("ultimateMachines").isJsonArray());

            // 6. Verify perk machines include key perks
            JsonArray perks = root.getAsJsonArray("perkMachines");
            Set<String> perkTypes = new HashSet<>();
            for (JsonElement el : perks) {
                JsonObject p = el.getAsJsonObject();
                if (p.has("properties") && p.getAsJsonObject("properties").has("perk")) {
                    perkTypes.add(p.getAsJsonObject("properties").get("perk").getAsString());
                }
            }
            Assert.assertTrue("Must contain SPEED perk", perkTypes.contains("SPEED"));
            Assert.assertTrue("Must contain EXTRA_HEALTH perk", perkTypes.contains("EXTRA_HEALTH"));
            Assert.assertTrue("Must contain FAST_REVIVE perk", perkTypes.contains("FAST_REVIVE"));
            Assert.assertTrue("Must contain QUICK_FIRE perk", perkTypes.contains("QUICK_FIRE"));
        } catch (Exception e) {
            Assert.fail("Failed to validate dead_end.json template: " + e.getMessage());
        }
    }
}

