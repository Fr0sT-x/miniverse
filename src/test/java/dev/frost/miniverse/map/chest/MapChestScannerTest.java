package dev.frost.miniverse.map.chest;

import dev.frost.miniverse.map.editor.MapMarker;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.MarkerType;
import net.minecraft.util.math.BlockPos;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class MapChestScannerTest {

    @Test
    public void testToMarkersConversion() {
        MarkerDefinition def = new MarkerDefinition(
            "test_chests", "Test Chests", MarkerType.POINT, "testChests", 0, 100, null,
            "• Purpose: Test marker conversion.\n• Placement: Anywhere."
        );

        List<BlockPos> positions = List.of(
            new BlockPos(10, 64, 20),
            new BlockPos(-5, 70, 15)
        );

        List<MapMarker> markers = MapChestScanner.toMarkers(def, positions);
        Assert.assertEquals(2, markers.size());

        MapMarker first = markers.get(0);
        Assert.assertEquals("test_chests", first.definitionKey());
        Assert.assertEquals("Test Chests #1", first.name());
        Assert.assertFalse(first.points().isEmpty());
        Assert.assertEquals(10.5, first.points().get(0).x(), 0.001);
        Assert.assertEquals(64.0, first.points().get(0).y(), 0.001);
        Assert.assertEquals(20.5, first.points().get(0).z(), 0.001);

        MapMarker second = markers.get(1);
        Assert.assertEquals("test_chests", second.definitionKey());
        Assert.assertEquals("Test Chests #2", second.name());
        Assert.assertEquals(-4.5, second.points().get(0).x(), 0.001);
    }

    @Test
    public void testEmptyAndNullHandling() {
        MarkerDefinition def = new MarkerDefinition(
            "test_chests", "Test Chests", MarkerType.POINT, "testChests", 0, 100, null,
            "• Purpose: Test null handling.\n• Placement: Anywhere."
        );

        Assert.assertTrue(MapChestScanner.toMarkers(def, null).isEmpty());
        Assert.assertTrue(MapChestScanner.toMarkers(null, List.of(new BlockPos(0, 0, 0))).isEmpty());
        Assert.assertTrue(MapChestScanner.toMarkers(def, List.of()).isEmpty());
    }

    @Test
    public void testScanResultImmutability() {
        MapChestScanner.ScanResult result = new MapChestScanner.ScanResult(
            List.of(new BlockPos(1, 2, 3)),
            List.of(new BlockPos(4, 5, 6)),
            2
        );

        Assert.assertEquals(1, result.islandChests().size());
        Assert.assertEquals(1, result.midChests().size());
        Assert.assertEquals(2, result.totalFound());
    }
}
