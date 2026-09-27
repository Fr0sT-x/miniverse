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
    MarkerGrouping grouping,
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
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("MarkerDefinition '" + key + "' must provide a non-blank description explaining its purpose and placement to map creators.");
        }
        description = description.trim();
    }

    public MarkerDefinition(
        String key,
        String displayName,
        MarkerType type,
        String configKey,
        int minCount,
        int maxCount,
        List<TriggerType> triggers,
        String description
    ) {
        this(key, displayName, type, configKey, minCount, maxCount, triggers, null, description);
    }

    public boolean single() {
        return this.maxCount == 1;
    }

    public static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
