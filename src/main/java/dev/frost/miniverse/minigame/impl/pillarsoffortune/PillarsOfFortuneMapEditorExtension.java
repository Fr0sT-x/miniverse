package dev.frost.miniverse.minigame.impl.pillarsoffortune;

import dev.frost.miniverse.map.editor.MapEditorExtension;
import dev.frost.miniverse.map.editor.MarkerDefinition;
import dev.frost.miniverse.map.editor.MarkerType;

import java.util.List;

public final class PillarsOfFortuneMapEditorExtension {
    public static final String SPAWN_POINT = "spawn_point";

    public static final MapEditorExtension EXTENSION = new MapEditorExtension(
        PillarsOfFortuneDefinition.ID,
        PillarsOfFortuneDefinition.DISPLAY_NAME,
        List.of(
            new MarkerDefinition(
                SPAWN_POINT, "Pillar Spawn", MarkerType.POINT, "spawns", 2, Integer.MAX_VALUE, null, null,
                "Spawn point on top of an isolated pillar platform.\n" +
                "• Required: At least 2 points (2 or more; 8 to 24 recommended).\n" +
                "• Purpose: Each player spawns on their own pillar and receives random items over time to battle across the void.\n" +
                "• Placement: Place centered on the top block of each pillar facing toward the center of the ring of pillars."
            ),
            new MarkerDefinition(
                "custom_region", "Custom Region", MarkerType.REGION, "customRegions", 0, Integer.MAX_VALUE, null,
                "Custom bounding region for map-specific building or breaking rules.\n" +
                "• Optional: Up to unlimited regions.\n" +
                "• Purpose: Allows map creators to apply custom flags like BREAK_DENIED to protect pillar bases or decorative structures.\n" +
                "• Placement: Enclose decorative map terrain or central structures where block editing should be forbidden."
            )
        ),
        List.of()
    );

    private PillarsOfFortuneMapEditorExtension() {}
}
