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
        ZombiesSettings original = new ZombiesSettings("dead_end", 750, 30, 15, 35, false);
        Properties props = new Properties();
        original.writeTo(props);

        ZombiesSettings restored = ZombiesSettings.fromProperties(props);
        Assert.assertEquals("dead_end", restored.mapId());
        Assert.assertEquals(750, restored.startGold());
        Assert.assertEquals(30, restored.maxRounds());
        Assert.assertEquals(15, restored.intermissionSeconds());
        Assert.assertEquals(35, restored.bleedoutSeconds());
        Assert.assertFalse(restored.friendlyFire());
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
    }

    @Test
    public void testBundledTemplateJsonIntegrity() {
        try (InputStream stream = getClass().getResourceAsStream("/templates/dead_end.json")) {
            Assert.assertNotNull("Bundled dead_end.json must be present on classpath", stream);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();

            // 1. Verify start area
            Assert.assertTrue("Root must have startArea", root.has("startArea"));
            Assert.assertEquals("Alley", root.get("startArea").getAsString());

            // 2. Verify doors
            Assert.assertTrue("Root must have doors", root.has("doors") && root.get("doors").isJsonArray());
            JsonArray doors = root.getAsJsonArray("doors");
            Assert.assertTrue("Must have at least 5 doors", doors.size() >= 5);
            Set<String> referencedAreas = new HashSet<>();
            referencedAreas.add(root.get("startArea").getAsString());

            for (JsonElement el : doors) {
                JsonObject d = el.getAsJsonObject();
                Assert.assertTrue(d.has("area1"));
                Assert.assertTrue(d.has("area2"));
                Assert.assertTrue(d.has("gold"));
                Assert.assertTrue(d.has("position"));
                referencedAreas.add(d.get("area1").getAsString());
                referencedAreas.add(d.get("area2").getAsString());
            }

            // 3. Verify windows
            Assert.assertTrue("Root must have windows", root.has("windows") && root.get("windows").isJsonArray());
            JsonArray windows = root.getAsJsonArray("windows");
            Assert.assertTrue("Must have at least 10 barricade windows", windows.size() >= 10);
            for (JsonElement el : windows) {
                JsonObject w = el.getAsJsonObject();
                Assert.assertTrue(w.has("area"));
                Assert.assertTrue(w.has("spawnLocation") || w.has("spawn"));
                Assert.assertTrue(w.has("blocks"));
                Assert.assertTrue(w.has("repairArea"));
            }

            // 4. Verify stations
            Assert.assertTrue("Must have weaponShops", root.has("weaponShops") && root.get("weaponShops").isJsonArray());
            Assert.assertTrue("Must have armorShops", root.has("armorShops") && root.get("armorShops").isJsonArray());
            Assert.assertTrue("Must have perkMachines", root.has("perkMachines") && root.get("perkMachines").isJsonArray());
            Assert.assertTrue("Must have luckyChests", root.has("luckyChests") && root.get("luckyChests").isJsonArray());
            Assert.assertTrue("Must have powerSwitch", root.has("powerSwitch") && root.get("powerSwitch").isJsonObject());
            Assert.assertTrue("Must have teamMachine", root.has("teamMachine") && root.get("teamMachine").isJsonObject());

            // 5. Verify perk machines include key perks
            JsonArray perks = root.getAsJsonArray("perkMachines");
            Set<String> perkTypes = new HashSet<>();
            for (JsonElement el : perks) {
                JsonObject p = el.getAsJsonObject();
                perkTypes.add(p.get("perk").getAsString());
            }
            Assert.assertTrue("Must contain SPEED perk", perkTypes.contains("SPEED"));
            Assert.assertTrue("Must contain EXTRA_HEALTH perk", perkTypes.contains("EXTRA_HEALTH"));
            Assert.assertTrue("Must contain FAST_REVIVE perk", perkTypes.contains("FAST_REVIVE"));
            Assert.assertTrue("Must contain QUICK_FIRE perk", perkTypes.contains("QUICK_FIRE"));
        } catch (Exception e) {
            Assert.fail("Failed to load and validate dead_end.json template: " + e.getMessage());
        }
    }
}

