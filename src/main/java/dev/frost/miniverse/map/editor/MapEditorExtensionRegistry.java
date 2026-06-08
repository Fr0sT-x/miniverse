package dev.frost.miniverse.map.editor;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MapEditorExtensionRegistry {
    private static final Map<String, MapEditorExtension> EXTENSIONS = new LinkedHashMap<>();

    private MapEditorExtensionRegistry() {
    }

    public static synchronized void register(MapEditorExtension extension) {
        if (extension == null || extension.gameId().isBlank()) {
            return;
        }
        EXTENSIONS.put(extension.gameId(), extension);
    }

    public static synchronized Optional<MapEditorExtension> get(String gameId) {
        String normalized = gameId == null ? "" : gameId.trim().toLowerCase(java.util.Locale.ROOT);
        return Optional.ofNullable(EXTENSIONS.get(normalized));
    }

    public static synchronized Collection<MapEditorExtension> all() {
        return List.copyOf(EXTENSIONS.values());
    }
}
