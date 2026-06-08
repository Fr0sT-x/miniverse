package dev.frost.miniverse.map.editor;

import java.util.List;
import java.util.Locale;

public record MapEditorExtension(
    String gameId,
    String displayName,
    List<MarkerDefinition> markers,
    List<MapEditorValidator> validators
) {
    public MapEditorExtension {
        gameId = gameId == null ? "" : gameId.trim().toLowerCase(Locale.ROOT);
        displayName = displayName == null || displayName.isBlank() ? gameId : displayName.trim();
        markers = markers == null ? List.of() : List.copyOf(markers);
        validators = validators == null ? List.of() : List.copyOf(validators);
    }

    public java.util.Optional<MarkerDefinition> marker(String key) {
        String normalized = MarkerDefinition.normalizeKey(key);
        return this.markers.stream().filter(marker -> marker.key().equals(normalized)).findFirst();
    }
}
