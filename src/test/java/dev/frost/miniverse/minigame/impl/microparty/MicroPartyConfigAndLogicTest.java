package dev.frost.miniverse.minigame.impl.microparty;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.RegionPart;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRuleRegistry;
import dev.frost.miniverse.minigame.impl.microparty.rule.impl.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class MicroPartyConfigAndLogicTest {

    @Test
    public void testSettingsSerialization() {
        MicroPartySettings original = new MicroPartySettings("disco_arena", 5, 30, "SURVIVAL", true, 3);
        Properties props = new Properties();
        original.writeTo(props);

        MicroPartySettings restored = MicroPartySettings.fromProperties(props);
        Assert.assertEquals("disco_arena", restored.mapId());
        Assert.assertEquals(5, restored.startingLives());
        Assert.assertEquals(30, restored.maxRounds());
        Assert.assertEquals("SURVIVAL", restored.gameMode());
        Assert.assertTrue(restored.speedScaling());
        Assert.assertEquals(3, restored.intermissionSeconds());
    }

    @Test
    public void testSettingsDefaults() {
        MicroPartySettings defaults = MicroPartySettings.defaults();
        Assert.assertEquals("", defaults.mapId());
        Assert.assertEquals(3, defaults.startingLives());
        Assert.assertEquals(25, defaults.maxRounds());
        Assert.assertEquals("SURVIVAL", defaults.gameMode());
        Assert.assertTrue(defaults.speedScaling());
        Assert.assertEquals(2, defaults.intermissionSeconds());
    }

    @Test
    public void testMapConfigParsingAndValidation() {
        JsonObject root = new JsonObject();

        // arenaBounds
        JsonArray boundsArr = new JsonArray();
        JsonObject bound = new JsonObject();
        JsonArray regions = new JsonArray();
        JsonObject r = new JsonObject();
        r.add("min", MapPosition.of(-15, 60, -15).toJson());
        r.add("max", MapPosition.of(15, 90, 15).toJson());
        regions.add(r);
        bound.add("regions", regions);
        boundsArr.add(bound);
        root.add("arenaBounds", boundsArr);

        // arenaCenter
        JsonArray centerArr = new JsonArray();
        centerArr.add(MapPosition.of(0, 64, 0).toJson());
        root.add("arenaCenter", centerArr);

        // playerSpawns
        JsonArray spawnsArr = new JsonArray();
        spawnsArr.add(MapPosition.of(-5, 64, -5).toJson());
        spawnsArr.add(MapPosition.of(5, 64, 5).toJson());
        root.add("playerSpawns", spawnsArr);

        // colorZones
        JsonArray colorArr = new JsonArray();
        JsonObject redZone = new JsonObject();
        JsonObject redProps = new JsonObject();
        redProps.addProperty("color", "RED");
        redZone.add("properties", redProps);
        JsonArray redRegions = new JsonArray();
        JsonObject rr = new JsonObject();
        rr.add("min", MapPosition.of(-10, 64, -10).toJson());
        rr.add("max", MapPosition.of(-2, 66, -2).toJson());
        redRegions.add(rr);
        redZone.add("regions", redRegions);
        colorArr.add(redZone);
        root.add("colorZones", colorArr);

        MicroPartyMapConfig config = MicroPartyMapConfig.fromJson(root);
        Assert.assertFalse(config.arenaBounds().isEmpty());
        Assert.assertEquals(2, config.playerSpawns().size());
        Assert.assertEquals(1, config.colorZones().size());
        Assert.assertEquals("RED", config.colorZones().get(0).color());

        MapValidationResult validation = MicroPartyMapConfig.validate(null, root);
        Assert.assertTrue("Config should be valid: " + validation.errors(), validation.valid());
    }

    @Test
    public void testMapConfigSingleJsonObjectParsingAndValidation() {
        JsonObject root = new JsonObject();

        // arenaBounds as a single JsonObject (saved when maxCount == 1)
        JsonObject bound = new JsonObject();
        bound.addProperty("id", "test-bounds-id");
        bound.addProperty("name", "Arena Bounds");
        JsonArray regions = new JsonArray();
        JsonObject r = new JsonObject();
        r.add("min", MapPosition.of(-20, 97, -19).toJson());
        r.add("max", MapPosition.of(19, 97, 20).toJson());
        regions.add(r);
        bound.add("regions", regions);
        root.add("arenaBounds", bound);

        // arenaCenter as a single JsonObject (saved when maxCount == 1)
        JsonObject center = new JsonObject();
        center.addProperty("id", "test-center-id");
        center.addProperty("name", "Arena Center");
        center.addProperty("x", 0.0);
        center.addProperty("y", 98.0);
        center.addProperty("z", 0.0);
        root.add("arenaCenter", center);

        // playerSpawns as array
        JsonArray spawnsArr = new JsonArray();
        spawnsArr.add(MapPosition.of(-5, 98, 0).toJson());
        spawnsArr.add(MapPosition.of(5, 98, 0).toJson());
        root.add("playerSpawns", spawnsArr);

        MicroPartyMapConfig config = MicroPartyMapConfig.fromJson(root);
        Assert.assertFalse("arenaBounds should not be empty", config.arenaBounds().isEmpty());
        Assert.assertEquals(1, config.arenaBounds().size());
        Assert.assertEquals(0.0, config.arenaCenter().x(), 0.001);
        Assert.assertEquals(98.0, config.arenaCenter().y(), 0.001);
        Assert.assertEquals(2, config.playerSpawns().size());

        MapValidationResult validation = MicroPartyMapConfig.validate(null, root);
        Assert.assertTrue("Validation should pass: " + validation.errors(), validation.valid());
    }

    @Test
    public void testMapConfigValidationErrors() {
        JsonObject empty = new JsonObject();
        MapValidationResult validation = MicroPartyMapConfig.validate(null, empty);
        Assert.assertFalse(validation.valid());
        Assert.assertTrue(validation.errors().stream().anyMatch(e -> e.contains("Arena Bounds")));
    }

    @Test
    public void testMicroRuleRegistry() {
        MicroPartyMapConfig fullConfig = new MicroPartyMapConfig(
            List.of(new RegionPart(MapPosition.of(0, 0, 0), MapPosition.of(10, 10, 10))),
            MapPosition.of(5, 5, 5),
            List.of(MapPosition.of(1, 1, 1), MapPosition.of(2, 2, 2)),
            List.of(),
            List.of(new MicroPartyMapConfig.ColorZone("z1", "BLUE", List.of())),
            List.of(new RegionPart(MapPosition.of(0, 10, 0), MapPosition.of(5, 12, 5))),
            List.of()
        );

        List<MicroRule> applicable = MicroRuleRegistry.getApplicableRules(fullConfig);
        Assert.assertTrue("Should have at least 10 rules", applicable.size() >= 10);
        Assert.assertTrue(applicable.stream().anyMatch(r -> r.id().equals("statue")));
        Assert.assertTrue(applicable.stream().anyMatch(r -> r.id().equals("color_rush")));
        Assert.assertTrue(applicable.stream().anyMatch(r -> r.id().equals("high_ground")));
    }

    @Test
    public void testPlayerPerformanceTracker() {
        PlayerPerformanceTracker tracker = new PlayerPerformanceTracker(3);
        UUID player1 = UUID.randomUUID();
        tracker.initPlayer(player1);

        Assert.assertEquals(3, tracker.getLives(player1));
        Assert.assertTrue(tracker.isAlive(player1));

        int livesLeft = tracker.deductLife(player1);
        Assert.assertEquals(2, livesLeft);
        Assert.assertEquals(1, tracker.getFails(player1));

        tracker.recordPass(player1, 100);
        Assert.assertEquals(1, tracker.getPasses(player1));
        Assert.assertEquals(100, tracker.getPoints(player1));
        Assert.assertTrue(tracker.hasPassedCurrentRound(player1));

        tracker.incrementSneak(player1);
        tracker.incrementSneak(player1);
        Assert.assertEquals(2, tracker.getSneakCount(player1));

        tracker.incrementJump(player1);
        Assert.assertEquals(1, tracker.getJumpCount(player1));

        tracker.resetRoundState(player1);
        Assert.assertFalse(tracker.hasPassedCurrentRound(player1));
        Assert.assertEquals(0, tracker.getSneakCount(player1));
        Assert.assertEquals(0, tracker.getJumpCount(player1));
        // Persistent counts should remain untouched
        Assert.assertEquals(2, tracker.getLives(player1));
        Assert.assertEquals(1, tracker.getPasses(player1));
    }

    @Test
    public void testMarkerDefinitionsAndDescriptions() {
        var markers = MicroPartyDefinition.EXTENSION.markers();
        Assert.assertEquals(7, markers.size());

        List<String> expectedKeys = List.of(
            MicroPartyDefinition.ARENA_BOUNDS,
            MicroPartyDefinition.ARENA_CENTER,
            MicroPartyDefinition.PLAYER_SPAWN,
            MicroPartyDefinition.LOBBY_SPAWN,
            MicroPartyDefinition.COLOR_ZONE,
            MicroPartyDefinition.HIGH_GROUND,
            MicroPartyDefinition.TARGET_POINT
        );

        for (String expectedKey : expectedKeys) {
            var markerOpt = markers.stream().filter(m -> m.key().equals(expectedKey)).findFirst();
            Assert.assertTrue("Missing marker definition: " + expectedKey, markerOpt.isPresent());
            var marker = markerOpt.get();

            // Verify description is non-blank and provides actionable guide information
            String desc = marker.description();
            Assert.assertNotNull(desc);
            Assert.assertFalse("Description should not be blank for " + expectedKey, desc.isBlank());
            Assert.assertTrue("Description should include Purpose for " + expectedKey, desc.contains("Purpose:"));
            Assert.assertTrue("Description should include Placement for " + expectedKey, desc.contains("Placement:"));
            Assert.assertTrue("Description should include quantity info for " + expectedKey,
                desc.contains("Required:") || desc.contains("Optional:"));
        }
    }

    @Test
    public void testMarkerDescriptionNbtSerialization() {
        dev.frost.miniverse.map.editor.MapEditorExtensionRegistry.register(MicroPartyDefinition.EXTENSION);

        // Ensure that MapEditorNbt preserves the marker description across network sync
        net.minecraft.nbt.NbtList nbtExtensions = dev.frost.miniverse.map.editor.MapEditorNbt.extensionsToNbt();
        boolean foundMicroParty = false;

        for (int i = 0; i < nbtExtensions.size(); i++) {
            net.minecraft.nbt.NbtCompound comp = nbtExtensions.getCompound(i);
            if (MicroPartyDefinition.ID.equals(comp.getString("gameId"))) {
                foundMicroParty = true;
                net.minecraft.nbt.NbtList markerList = comp.getList("markers", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
                Assert.assertEquals(7, markerList.size());

                for (int m = 0; m < markerList.size(); m++) {
                    net.minecraft.nbt.NbtCompound markerCompound = markerList.getCompound(m);
                    String desc = markerCompound.getString("description");
                    Assert.assertFalse("NBT serialized description should not be empty", desc.isBlank());
                    Assert.assertTrue("NBT serialized description should contain Purpose", desc.contains("Purpose:"));
                }
            }
        }
        Assert.assertTrue("MicroParty extension must be registered in MapEditorExtensionRegistry", foundMicroParty);
    }

    @Test
    public void testFlatMapProceduralApplicability() {
        // Minimal flat map config with only arena bounds and 2 player spawns
        MicroPartyMapConfig flatConfig = new MicroPartyMapConfig(
            List.of(new RegionPart(MapPosition.of(-15, 99, -15), MapPosition.of(15, 115, 15))),
            null, // No arenaCenter set!
            List.of(MapPosition.of(-5, 100, -5), MapPosition.of(5, 100, 5)),
            List.of(MapPosition.of(0, 110, 0)),
            List.of(), // No color zones!
            List.of(), // No high ground!
            List.of()  // No targets!
        );

        List<MicroRule> applicable = MicroRuleRegistry.getApplicableRules(flatConfig);
        Assert.assertTrue("All rules should be applicable on a flat platform", applicable.size() >= 22);
        Assert.assertTrue(applicable.stream().anyMatch(r -> r.id().equals("color_rush")));
        Assert.assertTrue(applicable.stream().anyMatch(r -> r.id().equals("high_ground")));
        Assert.assertTrue(applicable.stream().anyMatch(r -> r.id().equals("target_shoot")));
        Assert.assertTrue(applicable.stream().anyMatch(r -> r.id().equals("collect_coin")));
        Assert.assertTrue(applicable.stream().anyMatch(r -> r.id().equals("hot_potato")));
    }

    @Test
    public void testArenaCenterCentroidFallback() {
        JsonObject json = new JsonObject();
        JsonObject bounds = new JsonObject();
        JsonArray regions = new JsonArray();
        JsonObject r = new JsonObject();
        JsonObject min = new JsonObject();
        min.addProperty("x", -20.0);
        min.addProperty("y", 90.0);
        min.addProperty("z", -10.0);
        JsonObject max = new JsonObject();
        max.addProperty("x", 20.0);
        max.addProperty("y", 120.0);
        max.addProperty("z", 30.0);
        r.add("min", min);
        r.add("max", max);
        regions.add(r);
        bounds.add("regions", regions);
        json.add("arenaBounds", bounds);

        JsonArray spawns = new JsonArray();
        JsonObject s1 = new JsonObject();
        s1.addProperty("x", 0.0);
        s1.addProperty("y", 95.0);
        s1.addProperty("z", 0.0);
        JsonObject s2 = new JsonObject();
        s2.addProperty("x", 2.0);
        s2.addProperty("y", 95.0);
        s2.addProperty("z", 2.0);
        spawns.add(s1);
        spawns.add(s2);
        json.add("playerSpawns", spawns);

        MicroPartyMapConfig parsed = MicroPartyMapConfig.fromJson(json);
        Assert.assertNotNull(parsed.arenaCenter());
        Assert.assertEquals(0.0, parsed.arenaCenter().x(), 0.01);
        Assert.assertEquals(10.0, parsed.arenaCenter().z(), 0.01);
        Assert.assertEquals(95.0, parsed.arenaCenter().y(), 0.01);
    }

    @Test
    public void testTemporaryBlockManagerBasics() {
        TemporaryBlockManager manager = new TemporaryBlockManager();
        Assert.assertFalse(manager.hasTrackedBlocks());
        Assert.assertEquals(0, manager.trackedBlockCount());
        manager.restoreAll(null);
        Assert.assertFalse(manager.hasTrackedBlocks());
    }

    @Test
    public void testMicroPartyArenaHelper2D() {
        MicroPartyMapConfig config = new MicroPartyMapConfig(
            List.of(new RegionPart(MapPosition.of(-10, 100, -20), MapPosition.of(10, 110, 20))),
            MapPosition.of(0, 100, 0),
            List.of(MapPosition.of(-5, 100, -5), MapPosition.of(5, 100, 5)),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );

        MicroPartyArenaHelper.ArenaBounds2D b2d = MicroPartyArenaHelper.getBounds2D(config);
        Assert.assertEquals(-10, b2d.minX());
        Assert.assertEquals(10, b2d.maxX());
        Assert.assertEquals(-20, b2d.minZ());
        Assert.assertEquals(20, b2d.maxZ());
        Assert.assertEquals(21, b2d.width());
        Assert.assertEquals(41, b2d.depth());
        Assert.assertEquals(0, b2d.centerX());
        Assert.assertEquals(0, b2d.centerZ());
        Assert.assertEquals(100, MicroPartyArenaHelper.getFloorY(config));
    }

    @Test
    public void testSpeedUpPhaseEnum() {
        Assert.assertNotNull(MicroPartyMinigame.Phase.valueOf("SPEED_UP"));
        Assert.assertNotNull(MicroPartyMinigame.Phase.valueOf("INTERMISSION"));
        Assert.assertNotNull(MicroPartyMinigame.Phase.valueOf("ANNOUNCEMENT"));
        Assert.assertNotNull(MicroPartyMinigame.Phase.valueOf("ACTIVE"));
        Assert.assertNotNull(MicroPartyMinigame.Phase.valueOf("RESOLVING"));
    }

    @Test
    public void testSmartScalingRules() {
        JumpCountRule jumpRule = new JumpCountRule();
        Assert.assertEquals(8, jumpRule.baseDurationSeconds());
        Assert.assertEquals(4, JumpCountRule.getRequiredJumps(null));
        Assert.assertTrue(jumpRule.instruction(null).getString().contains("4"));

        RapidCrouchRule crouchRule = new RapidCrouchRule();
        Assert.assertEquals(8, crouchRule.baseDurationSeconds());
        Assert.assertEquals(5, RapidCrouchRule.getRequiredCrouches(null));
        Assert.assertTrue(crouchRule.instruction(null).getString().contains("5"));

        SpinRule spinRule = new SpinRule();
        Assert.assertEquals(8, spinRule.baseDurationSeconds());
        Assert.assertEquals(340.0f, SpinRule.getRequiredRotation(null), 0.01f);
        Assert.assertTrue(spinRule.instruction(null).getString().contains("360°"));

        CenterStageRule centerRule = new CenterStageRule();
        Assert.assertEquals(8, centerRule.baseDurationSeconds());
        Assert.assertEquals(3.5, CenterStageRule.getMaxDistance(null), 0.01);

        CollectCoinRule coinRule = new CollectCoinRule();
        Assert.assertEquals(8, coinRule.baseDurationSeconds());
        Assert.assertEquals(2, CollectCoinRule.getRequiredCoins(null));

        HotPotatoRule potatoRule = new HotPotatoRule();
        Assert.assertEquals(8, potatoRule.baseDurationSeconds());
        // Verify potato rule duration clamped to at least 100 ticks (5s) even if factor is low
        Assert.assertTrue(potatoRule.getDurationTicks(null) >= 100);

        ColorRushRule colorRule = new ColorRushRule();
        Assert.assertEquals(8, colorRule.baseDurationSeconds());
        Assert.assertTrue(colorRule.getDurationTicks(null) >= 80);

        HighGroundRule highGroundRule = new HighGroundRule();
        Assert.assertEquals(8, highGroundRule.baseDurationSeconds());
        Assert.assertTrue(highGroundRule.getDurationTicks(null) >= 80);

        TargetShootRule targetRule = new TargetShootRule();
        Assert.assertEquals(8, targetRule.baseDurationSeconds());
        Assert.assertTrue(targetRule.getDurationTicks(null) >= 70);

        PunchFriendRule punchRule = new PunchFriendRule();
        Assert.assertEquals(8, punchRule.baseDurationSeconds());
        Assert.assertTrue(punchRule.getDurationTicks(null) >= 80);
    }

    @Test
    public void testAnvilDodgeRuleFeatures() {
        AnvilDodgeRule anvilRule = new AnvilDodgeRule();
        Assert.assertEquals(8, anvilRule.baseDurationSeconds());
        Assert.assertEquals("anvil_dodge", anvilRule.id());
        Assert.assertTrue(anvilRule.title().getString().contains("ANVILS"));
        Assert.assertTrue(anvilRule.instruction().getString().contains("Watch"));
    }

    @Test
    public void testGetAllRulesMetadata() {
        List<MicroRule> all = MicroRuleRegistry.getAllRules();
        Assert.assertEquals("Should have exactly 38 micro-rules", 38, all.size());
        for (MicroRule rule : all) {
            Assert.assertNotNull("Rule id must not be null", rule.id());
            Assert.assertFalse("Rule id must not be blank", rule.id().isBlank());
            Assert.assertNotNull("Rule name must not be null for " + rule.id(), rule.name());
            Assert.assertFalse("Rule name must not be blank for " + rule.id(), rule.name().isBlank());
            Assert.assertNotNull("Rule description must not be null for " + rule.id(), rule.description());
            Assert.assertFalse("Rule description must not be blank for " + rule.id(), rule.description().isBlank());
        }
    }

    @Test
    public void testEnabledRulesSettingsSerialization() {
        MicroPartySettings settings = new MicroPartySettings(
            "test_map", 3, 20, "SURVIVAL", true, 2, java.util.Set.of("statue", "jump_count")
        );
        Assert.assertTrue(settings.isRuleEnabled("statue"));
        Assert.assertTrue(settings.isRuleEnabled("jump_count"));
        Assert.assertFalse(settings.isRuleEnabled("hot_potato"));

        // Properties serialization
        Properties props = new Properties();
        settings.writeTo(props);
        Assert.assertTrue(props.getProperty("microparty.enabledRules").contains("statue"));
        Assert.assertTrue(props.getProperty("microparty.enabledRules").contains("jump_count"));

        MicroPartySettings restored = MicroPartySettings.fromProperties(props);
        Assert.assertTrue(restored.isRuleEnabled("statue"));
        Assert.assertTrue(restored.isRuleEnabled("jump_count"));
        Assert.assertFalse(restored.isRuleEnabled("hot_potato"));

        // NBT serialization
        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putString("mapId", "test_map");
        nbt.putString("enabledRules", "statue,hot_potato");
        MicroPartySettings fromNbt = MicroPartySettings.fromNbt(nbt);
        Assert.assertTrue(fromNbt.isRuleEnabled("statue"));
        Assert.assertTrue(fromNbt.isRuleEnabled("hot_potato"));
        Assert.assertFalse(fromNbt.isRuleEnabled("jump_count"));

        // Empty rules -> all enabled
        MicroPartySettings emptySettings = MicroPartySettings.defaults();
        Assert.assertTrue(emptySettings.isRuleEnabled("statue"));
        Assert.assertTrue(emptySettings.isRuleEnabled("anything"));
    }

    @Test
    public void testNewMicroRulesFeatures() {
        QuickMathRule mathRule = new QuickMathRule();
        Assert.assertEquals(8, mathRule.baseDurationSeconds());
        Assert.assertEquals("quick_math", mathRule.id());
        Assert.assertEquals("Quick Math", mathRule.name());
        mathRule.generateNewQuestion();
        Assert.assertNotNull(mathRule.getCurrentQuestion());
        Assert.assertTrue(mathRule.instruction().getString().contains(mathRule.getCurrentQuestion()));

        EatFoodRule eatRule = new EatFoodRule();
        Assert.assertEquals(8, eatRule.baseDurationSeconds());
        Assert.assertEquals("eat_food", eatRule.id());
        Assert.assertEquals("Feast", eatRule.name());

        EquipArmorRule equipRule = new EquipArmorRule();
        Assert.assertEquals(8, equipRule.baseDurationSeconds());
        Assert.assertEquals("equip_armor", equipRule.id());
        Assert.assertEquals("Gear Up", equipRule.name());

        KeepMovingRule keepRule = new KeepMovingRule();
        Assert.assertEquals(8, keepRule.baseDurationSeconds());
        Assert.assertEquals("keep_moving", keepRule.id());
        Assert.assertEquals("Don't Stop", keepRule.name());

        FindOddItemRule oddRule = new FindOddItemRule();
        Assert.assertEquals(8, oddRule.baseDurationSeconds());
        Assert.assertEquals("find_odd_item", oddRule.id());
        Assert.assertEquals("Find The Odd One", oddRule.name());

        BlastRadiusRule blastRule = new BlastRadiusRule();
        Assert.assertEquals(8, blastRule.baseDurationSeconds());
        Assert.assertEquals("blast_radius", blastRule.id());
        Assert.assertEquals("Blast Radius", blastRule.name());

        SnowballFightRule snowballRule = new SnowballFightRule();
        Assert.assertEquals(8, snowballRule.baseDurationSeconds());
        Assert.assertEquals("snowball_fight", snowballRule.id());
        Assert.assertEquals("Snowball Tag", snowballRule.name());
        Assert.assertTrue(snowballRule.description().contains("3"));

        SimonSaysRule simonRule = new SimonSaysRule();
        Assert.assertEquals(7, simonRule.baseDurationSeconds());
        Assert.assertEquals("simon_says", simonRule.id());
        Assert.assertEquals("Simon Says", simonRule.name());

        LawnMowerRule lawnRule = new LawnMowerRule();
        Assert.assertEquals(8, lawnRule.baseDurationSeconds());
        Assert.assertEquals("lawn_mower", lawnRule.id());
        Assert.assertEquals("Mow The Lawn", lawnRule.name());

        ChickenHuntRule chickenRule = new ChickenHuntRule();
        Assert.assertEquals(7, chickenRule.baseDurationSeconds());
        Assert.assertEquals("chicken_hunt", chickenRule.id());
        Assert.assertEquals("Punch a Chicken", chickenRule.name());

        StareDownRule stareRule = new StareDownRule();
        Assert.assertEquals(6, stareRule.baseDurationSeconds());
        Assert.assertEquals("stare_down", stareRule.id());
        Assert.assertEquals("Stare Down", stareRule.name());

        SweeperBarRule sweeperRule = new SweeperBarRule();
        Assert.assertEquals(7, sweeperRule.baseDurationSeconds());
        Assert.assertEquals("sweeper_bar", sweeperRule.id());
        Assert.assertEquals("Jump the Sweeper", sweeperRule.name());

        CountMobsRule countRule = new CountMobsRule();
        Assert.assertEquals(7, countRule.baseDurationSeconds());
        Assert.assertEquals("count_mobs", countRule.id());
        Assert.assertEquals("Count the Sheep", countRule.name());

        WordScrambleRule scrambleRule = new WordScrambleRule();
        Assert.assertEquals(8, scrambleRule.baseDurationSeconds());
        Assert.assertEquals("word_scramble", scrambleRule.id());
        Assert.assertEquals("Word Scramble", scrambleRule.name());

        EchoRule echoRule = new EchoRule();
        Assert.assertEquals(8, echoRule.baseDurationSeconds());
        Assert.assertEquals("echo_phrase", echoRule.id());
        Assert.assertEquals("Echo", echoRule.name());

        ColorRouletteRule rouletteRule = new ColorRouletteRule();
        Assert.assertEquals(8, rouletteRule.baseDurationSeconds());
        Assert.assertEquals("color_roulette", rouletteRule.id());
        Assert.assertEquals("Color Roulette", rouletteRule.name());

        FishingHookRule fishingRule = new FishingHookRule();
        Assert.assertEquals(8, fishingRule.baseDurationSeconds());
        Assert.assertEquals("fishing_hook", fishingRule.id());
        Assert.assertEquals("Reel 'Em In", fishingRule.name());

        MortarStrikeRule mortarRule = new MortarStrikeRule();
        Assert.assertEquals(8, mortarRule.baseDurationSeconds());
        Assert.assertEquals("mortar_strike", mortarRule.id());
        Assert.assertEquals("Mortar Strike", mortarRule.name());

        MlgBucketRule mlgRule = new MlgBucketRule();
        Assert.assertEquals(7, mlgRule.baseDurationSeconds());
        Assert.assertEquals("mlg_bucket", mlgRule.id());
        Assert.assertEquals("MLG Water Drop", mlgRule.name());

        MusicalBoatsRule boatRule = new MusicalBoatsRule();
        Assert.assertEquals(7, boatRule.baseDurationSeconds());
        Assert.assertEquals("musical_boats", boatRule.id());
        Assert.assertEquals("Musical Boats", boatRule.name());

        StopClockRule clockRule = new StopClockRule();
        Assert.assertEquals(5, clockRule.baseDurationSeconds());
        Assert.assertEquals("stop_clock", clockRule.id());
        Assert.assertEquals("Stop the Clock", clockRule.name());

        DarknessButtonRule darkRule = new DarknessButtonRule();
        Assert.assertEquals(8, darkRule.baseDurationSeconds());
        Assert.assertEquals("darkness_button", darkRule.id());
        Assert.assertEquals("Blind Button", darkRule.name());
        Assert.assertTrue(darkRule.title().getString().contains("BUTTON"));
    }

    @Test
    public void testTotalRuleCountAndExclusions() {
        List<MicroRule> all = MicroRuleRegistry.getAllRules();
        Assert.assertEquals(38, all.size());
        Assert.assertTrue(all.stream().noneMatch(r -> r.id().equals("floor_vanish")));
    }

    @Test
    public void testChatInterceptAwareRules() {
        MicroPartyMinigame minigame = new MicroPartyMinigame();
        Assert.assertTrue(minigame instanceof dev.frost.miniverse.chat.ChatInterceptAware);
        Assert.assertFalse(minigame instanceof dev.frost.miniverse.chat.ChatRoutingAware);

        QuickMathRule mathRule = new QuickMathRule();
        Assert.assertNotNull(mathRule);

        WordScrambleRule scrambleRule = new WordScrambleRule();
        Assert.assertNotNull(scrambleRule);

        EchoRule echoRule = new EchoRule();
        Assert.assertNotNull(echoRule);

        CountMobsRule countRule = new CountMobsRule();
        Assert.assertNotNull(countRule);
    }

    @Test
    public void testEchoRuleOnPrepareAndInstructionSync() {
        EchoRule rule = new EchoRule();
        rule.onPrepare(null, null);

        String instruction = rule.instruction().getString();
        Assert.assertTrue("Instruction must start with Type:", instruction.startsWith("Type:"));
        Assert.assertTrue("Instruction must contain a quoted phrase", instruction.contains("\""));

        // Extract phrase between quotes
        int start = instruction.indexOf("\"");
        int end = instruction.lastIndexOf("\"");
        Assert.assertTrue("Quote indices valid", start >= 0 && end > start);
        String phrase = instruction.substring(start + 1, end);
        Assert.assertFalse("Target phrase should not be empty", phrase.isBlank());
    }

    @Test
    public void testWordScrambleRuleOnPrepare() {
        WordScrambleRule rule = new WordScrambleRule();
        rule.onPrepare(null, null);

        String title = rule.title().getString();
        Assert.assertTrue("Title must contain UNSCRAMBLE:", title.contains("UNSCRAMBLE:"));
        String scrambled = title.replace("UNSCRAMBLE:", "").trim();
        Assert.assertFalse("Scrambled word should not be empty", scrambled.isBlank());
    }

    @Test
    public void testQuickMathRuleOnPrepare() {
        QuickMathRule rule = new QuickMathRule();
        rule.onPrepare(null, null);

        String instruction = rule.instruction().getString();
        Assert.assertTrue("Instruction must contain What is", instruction.contains("What is"));
        Assert.assertNotNull("Expected question should exist", rule.getCurrentQuestion());
        Assert.assertTrue("Instruction must reflect the generated question", instruction.contains(rule.getCurrentQuestion()));
    }

    @Test
    public void testMlgBucketRuleProperties() {
        MlgBucketRule rule = new MlgBucketRule();
        Assert.assertEquals("mlg_bucket", rule.id());
        Assert.assertEquals("MLG Water Drop", rule.name());
        Assert.assertEquals(7, rule.baseDurationSeconds());
        Assert.assertTrue(rule.title().getString().contains("MLG WATER DROP"));
        Assert.assertTrue(rule.instruction().getString().contains("Water drop"));
    }

    @Test
    public void testRuleRecencyStrictExclusionAndLowerPriority() {
        MicroPartyMinigame game = new MicroPartyMinigame();
        List<MicroRule> pool = MicroRuleRegistry.getAllRules().subList(0, 10);

        // Run 50 simulated selections
        for (int round = 1; round <= 50; round++) {
            List<String> historyBefore = game.getRecentRuleHistory();
            int hSize = historyBefore.size();

            MicroRule picked = game.selectNextRule(pool);
            Assert.assertNotNull("Selected rule should not be null", picked);

            // Verify the selected rule was NOT in the last 3 rounds
            if (hSize >= 1) {
                Assert.assertNotEquals("Must not appear 1 round ago", historyBefore.get(hSize - 1), picked.id());
            }
            if (hSize >= 2) {
                Assert.assertNotEquals("Must not appear 2 rounds ago", historyBefore.get(hSize - 2), picked.id());
            }
            if (hSize >= 3) {
                Assert.assertNotEquals("Must not appear 3 rounds ago", historyBefore.get(hSize - 3), picked.id());
            }
        }

        // Verify priority / weight curve with distinct rule IDs
        MicroPartyMinigame weightGame = new MicroPartyMinigame();
        JsonObject state = new JsonObject();
        JsonArray arr = new JsonArray();
        arr.add("rule_6_ago"); // 6 rounds ago
        arr.add("rule_5_ago"); // 5 rounds ago
        arr.add("rule_4_ago"); // 4 rounds ago
        arr.add("rule_3_ago"); // 3 rounds ago
        arr.add("rule_2_ago"); // 2 rounds ago
        arr.add("rule_1_ago"); // 1 round ago
        state.add("recentRuleHistory", arr);
        weightGame.loadRuntimeState(state);

        Assert.assertEquals("Rule 4 rounds ago should have weight 2", 2, weightGame.getRuleSelectionWeight("rule_4_ago"));
        Assert.assertEquals("Rule 5 rounds ago should have weight 4", 4, weightGame.getRuleSelectionWeight("rule_5_ago"));
        Assert.assertEquals("Rule 6 rounds ago should have weight 7", 7, weightGame.getRuleSelectionWeight("rule_6_ago"));
        Assert.assertEquals("Unplayed rule should have full weight 10", 10, weightGame.getRuleSelectionWeight("unplayed_rule_xyz"));
    }

    @Test
    public void testSmallRulePoolFallback() {
        MicroPartyMinigame game = new MicroPartyMinigame();
        List<MicroRule> pool2 = MicroRuleRegistry.getAllRules().subList(0, 2);

        // Even with only 2 rules, selection shouldn't fail or get stuck
        for (int i = 0; i < 10; i++) {
            MicroRule picked = game.selectNextRule(pool2);
            Assert.assertNotNull(picked);
        }

        // With only 1 rule, selection returns the only rule
        List<MicroRule> pool1 = MicroRuleRegistry.getAllRules().subList(0, 1);
        MicroPartyMinigame game1 = new MicroPartyMinigame();
        for (int i = 0; i < 5; i++) {
            MicroRule picked = game1.selectNextRule(pool1);
            Assert.assertEquals(pool1.get(0).id(), picked.id());
        }
    }

    @Test
    public void testEquipArmorRuleInventoryDescription() {
        EquipArmorRule rule = new EquipArmorRule();
        Assert.assertEquals("equip_armor", rule.id());
        Assert.assertEquals("Gear Up", rule.name());
        Assert.assertFalse("Description should not mention hotbar", rule.description().toLowerCase().contains("hotbar"));
        Assert.assertTrue("Description should mention inventory", rule.description().toLowerCase().contains("inventory"));
        Assert.assertTrue("Instruction should mention inventory", rule.instruction().getString().toLowerCase().contains("inventory"));
    }

    @Test
    public void testStatePersistenceWithRecentRuleHistory() {
        MicroPartyMinigame game = new MicroPartyMinigame();
        List<MicroRule> pool = MicroRuleRegistry.getAllRules().subList(0, 5);

        for (int i = 0; i < 5; i++) {
            game.selectNextRule(pool);
        }

        JsonObject saved = game.saveRuntimeState();
        Assert.assertTrue(saved.has("recentRuleHistory"));
        Assert.assertTrue(saved.getAsJsonArray("recentRuleHistory").size() > 0);

        MicroPartyMinigame restoredGame = new MicroPartyMinigame();
        restoredGame.loadRuntimeState(saved);
        Assert.assertEquals(game.getRecentRuleHistory().size(), restoredGame.getRecentRuleHistory().size());
        Assert.assertEquals(game.getRecentRuleHistory(), restoredGame.getRecentRuleHistory());
    }
}
