package dev.frost.miniverse.map.editor;

import dev.frost.miniverse.map.region.TriggerType;

import java.util.List;
import java.util.Locale;

public record MarkerDefinition(
    String key,
    String displayName,
    MarkerType type,
    String configKey,
    int minCount,
    int maxCount,
    List<TriggerType> triggers,
    String description
) {
    public MarkerDefinition {
        key = normalizeKey(key);
        displayName = displayName == null || displayName.isBlank() ? key : displayName.trim();
        type = type == null ? MarkerType.POINT : type;
        configKey = configKey == null || configKey.isBlank() ? key : configKey.trim();
        minCount = Math.max(0, minCount);
        maxCount = maxCount <= 0 ? Integer.MAX_VALUE : maxCount;
        if (maxCount < minCount) {
            maxCount = minCount;
        }
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        description = description == null ? "" : description.trim();
    }

    public boolean single() {
        return this.maxCount == 1;
    }

    public static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
