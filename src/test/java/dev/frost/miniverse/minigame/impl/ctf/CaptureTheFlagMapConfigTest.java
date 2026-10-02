package dev.frost.miniverse.minigame.impl.ctf;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.map.MapValidationResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.junit.Assert;
import org.junit.Test;

public class CaptureTheFlagMapConfigTest {

    @Test
    public void testEmptyJsonDefaults() {
        CaptureTheFlagMapConfig config = CaptureTheFlagMapConfig.fromJson(new JsonObject());
        Assert.assertTrue(config.teams().isEmpty());
        Assert.assertTrue(config.powerupLocations().isEmpty());
        Assert.assertNull(config.voidLevelRef());
    }

    @Test
    public void testValidMultiTeamJson() {
        String json = """
        {
            "teamConfigs": [
                {
                    "id": "red",
                    "name": "Red Team",
                    "properties": { "teamId": "red", "color": "red" }
                },
                {
                    "id": "blue",
                    "name": "Blue Team",
                    "properties": { "teamId": "blue", "color": "blue" }
                }
            ],
            "teamSpawns": [
                {
                    "properties": { "teamId": "red" },
                    "points": [ {"x": 10.5, "y": 64.0, "z": 20.5, "yaw": 0.0, "pitch": 0.0} ]
                },
                {
                    "properties": { "teamId": "blue" },
                    "points": [ {"x": -10.5, "y": 64.0, "z": -20.5, "yaw": 180.0, "pitch": 0.0} ]
                }
            ],
            "teamFlags": [
                {
                    "properties": { "teamId": "red" },
                    "points": [ {"x": 10.0, "y": 64.0, "z": 25.0} ]
                },
                {
                    "properties": { "teamId": "blue" },
                    "points": [ {"x": -10.0, "y": 64.0, "z": -25.0} ]
                }
            ],
            "teamDropoffs": [
                {
                    "properties": { "teamId": "red" },
                    "points": [ {"x": 10.0, "y": 64.0, "z": 25.0} ]
                },
                {
                    "properties": { "teamId": "blue" },
                    "points": [ {"x": -10.0, "y": 64.0, "z": -25.0} ]
                }
            ],
            "shopNpcs": [
                {
                    "properties": { "teamId": "red" },
                    "points": [ {"x": 8.5, "y": 64.0, "z": 18.5, "yaw": 90.0, "pitch": 0.0} ]
                }
            ],
            "powerupLocations": [
                {
                    "points": [ {"x": 0.5, "y": 65.0, "z": 0.5, "yaw": 0.0, "pitch": 0.0} ]
                }
            ],
            "voidLevel": {
                "points": [ {"x": 0.0, "y": 0.0, "z": 0.0} ]
            }
        }
        """;

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        CaptureTheFlagMapConfig config = CaptureTheFlagMapConfig.fromJson(obj);

        Assert.assertEquals(2, config.teams().size());
        Assert.assertTrue(config.teams().containsKey("red"));
        Assert.assertTrue(config.teams().containsKey("blue"));

        CaptureTheFlagMapConfig.CtfTeamConfig redTeam = config.teams().get("red");
        Assert.assertEquals("Red Team", redTeam.name);
        Assert.assertEquals(Formatting.RED, redTeam.color);
        Assert.assertEquals(1, redTeam.spawns.size());
        Assert.assertEquals(new BlockPos(10, 64, 25), redTeam.flagPos);
        Assert.assertEquals(new BlockPos(10, 64, 25), redTeam.getEffectiveDropoffPos());
        Assert.assertEquals(1, redTeam.shopNpcs.size());

        CaptureTheFlagMapConfig.CtfTeamConfig blueTeam = config.teams().get("blue");
        Assert.assertEquals(new BlockPos(-10, 64, -25), blueTeam.flagPos);
        Assert.assertTrue(blueTeam.shopNpcs.isEmpty());

        Assert.assertEquals(1, config.powerupLocations().size());
        Assert.assertEquals(Integer.valueOf(0), config.voidLevelRef());

        // Validate map is complete and valid
        MapValidationResult validationResult = config.validate();
        Assert.assertTrue(validationResult.errors().toString(), validationResult.valid());
    }

    @Test
    public void testMapValidationFailsWithInsufficientTeams() {
        String json = """
        {
            "teamConfigs": [
                {
                    "id": "solo",
                    "name": "Solo Team",
                    "properties": { "teamId": "solo" }
                }
            ]
        }
        """;

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        CaptureTheFlagMapConfig config = CaptureTheFlagMapConfig.fromJson(obj);
        MapValidationResult result = config.validate();
        Assert.assertFalse(result.valid());
        Assert.assertTrue(result.errors().stream().anyMatch(e -> e.contains("at least 2 teams")));
    }

    @Test
    public void testSingleJsonObjectTeamFlagParsedProperly() {
        String json = """
        {
            "teamConfigs": [
                {
                    "id": "red",
                    "name": "Red Team",
                    "properties": { "teamId": "red", "color": "red" }
                },
                {
                    "id": "blue",
                    "name": "Blue Team",
                    "properties": { "teamId": "blue", "color": "blue" }
                }
            ],
            "teamSpawns": [
                {
                    "properties": { "teamId": "red" },
                    "points": [ {"x": 10.5, "y": 64.0, "z": 20.5, "yaw": 0.0, "pitch": 0.0} ]
                },
                {
                    "properties": { "teamId": "blue" },
                    "points": [ {"x": -10.5, "y": 64.0, "z": -20.5, "yaw": 180.0, "pitch": 0.0} ]
                }
            ],
            "teamFlags": {
                "properties": { "teamId": "red" },
                "points": [ {"x": 10.0, "y": 64.0, "z": 25.0} ]
            },
            "voidLevel": {
                "points": [ {"x": 0.0, "y": 0.0, "z": 0.0} ]
            }
        }
        """;

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        CaptureTheFlagMapConfig config = CaptureTheFlagMapConfig.fromJson(obj);

        Assert.assertNotNull(config.teams().get("red").flagPos);
        Assert.assertEquals(new BlockPos(10, 64, 25), config.teams().get("red").flagPos);
    }

    @Test
    public void testInvertedNpcYawCalculation() {
        // Player looking South (0 degrees) -> NPC should face North (180 or -180 degrees)
        float playerYawSouth = 0.0F;
        float npcYawSouth = net.minecraft.util.math.MathHelper.wrapDegrees(playerYawSouth + 180.0F);
        Assert.assertEquals(180.0F, Math.abs(npcYawSouth), 0.01F);

        // Player looking North (180 degrees) -> NPC should face South (0 degrees)
        float playerYawNorth = 180.0F;
        float npcYawNorth = net.minecraft.util.math.MathHelper.wrapDegrees(playerYawNorth + 180.0F);
        Assert.assertEquals(0.0F, npcYawNorth, 0.01F);

        // Player looking East (-90 degrees) -> NPC should face West (90 degrees)
        float playerYawEast = -90.0F;
        float npcYawEast = net.minecraft.util.math.MathHelper.wrapDegrees(playerYawEast + 180.0F);
        Assert.assertEquals(90.0F, npcYawEast, 0.01F);

        // Player looking West (90 degrees) -> NPC should face East (-90 degrees)
        float playerYawWest = 90.0F;
        float npcYawWest = net.minecraft.util.math.MathHelper.wrapDegrees(playerYawWest + 180.0F);
        Assert.assertEquals(-90.0F, npcYawWest, 0.01F);
    }
}
