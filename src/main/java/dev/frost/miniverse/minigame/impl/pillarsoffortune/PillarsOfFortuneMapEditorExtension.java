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
            new MarkerDefinition(SPAWN_POINT, "Pillar Spawn", MarkerType.POINT, "spawns", 2, Integer.MAX_VALUE, null, null, "Spawn point for a player. Place on top of a pillar."),
            new MarkerDefinition("custom_region", "Custom Region", MarkerType.REGION, "customRegions", 0, Integer.MAX_VALUE, null, "A generic region. Use this to apply custom restrictions like BREAK_DENIED anywhere on the map.")
        ),
        List.of()
    );

    private PillarsOfFortuneMapEditorExtension() {}
}
