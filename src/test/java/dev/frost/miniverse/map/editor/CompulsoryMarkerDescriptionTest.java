package dev.frost.miniverse.map.editor;

import dev.frost.miniverse.minigame.impl.bedwars.BedwarsDefinition;
import dev.frost.miniverse.minigame.impl.bridge.BridgeDefinition;
import dev.frost.miniverse.minigame.impl.dropper.DropperDefinition;
import dev.frost.miniverse.minigame.impl.duels.DuelsDefinition;
import dev.frost.miniverse.minigame.impl.infection.InfectionDefinition;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyDefinition;
import dev.frost.miniverse.minigame.impl.murdermystery.MurderMysteryDefinition;
import dev.frost.miniverse.minigame.impl.pillarsoffortune.PillarsOfFortuneMapEditorExtension;
import dev.frost.miniverse.minigame.impl.zombies.ZombiesDefinition;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

public class CompulsoryMarkerDescriptionTest {

    @BeforeClass
    public static void setup() {
        MapEditorExtensionRegistry.register(BedwarsDefinition.EXTENSION);
        MapEditorExtensionRegistry.register(BridgeDefinition.EXTENSION);
        MapEditorExtensionRegistry.register(DropperDefinition.EXTENSION);
        MapEditorExtensionRegistry.register(DuelsDefinition.EXTENSION);
        MapEditorExtensionRegistry.register(InfectionDefinition.EXTENSION);
        MapEditorExtensionRegistry.register(MicroFrenzyDefinition.EXTENSION);
        MapEditorExtensionRegistry.register(MurderMysteryDefinition.EXTENSION);
        MapEditorExtensionRegistry.register(PillarsOfFortuneMapEditorExtension.EXTENSION);
        MapEditorExtensionRegistry.register(ZombiesDefinition.EXTENSION);
    }

    @Test
    public void testAllRegisteredMarkersHaveValidDescriptions() {
        var extensions = MapEditorExtensionRegistry.all();
        Assert.assertFalse("Extensions should not be empty", extensions.isEmpty());

        for (MapEditorExtension extension : extensions) {
            Assert.assertFalse("Markers should not be empty for " + extension.gameId(), extension.markers().isEmpty());
            for (MarkerDefinition marker : extension.markers()) {
                String desc = marker.description();
                Assert.assertNotNull("Description cannot be null for " + marker.key() + " in " + extension.gameId(), desc);
                Assert.assertFalse("Description cannot be blank for " + marker.key() + " in " + extension.gameId(), desc.isBlank());

                // Verify it is actionable and descriptive (contains guidance bullets)
                Assert.assertTrue("Description for '" + marker.key() + "' in " + extension.gameId() + " should explain Purpose",
                    desc.contains("Purpose:"));
                Assert.assertTrue("Description for '" + marker.key() + "' in " + extension.gameId() + " should explain Placement",
                    desc.contains("Placement:"));
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullDescriptionThrows() {
        new MarkerDefinition("test_marker", "Test Marker", MarkerType.POINT, "testKey", 1, 1, List.of(), null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBlankDescriptionThrows() {
        new MarkerDefinition("test_marker", "Test Marker", MarkerType.POINT, "testKey", 1, 1, List.of(), null, "   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testShortConstructorBlankDescriptionThrows() {
        new MarkerDefinition("test_marker", "Test Marker", MarkerType.POINT, "testKey", 1, 1, List.of(), "");
    }
}
