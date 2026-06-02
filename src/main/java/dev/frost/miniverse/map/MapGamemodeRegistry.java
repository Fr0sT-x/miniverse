package dev.frost.miniverse.map;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MapGamemodeRegistry {
    private static final Map<String, MapGamemodeType> TYPES = new LinkedHashMap<>();

    private MapGamemodeRegistry() {
    }

    public static synchronized void register(MapGamemodeType type) {
        if (type == null || type.gameId().isBlank()) {
            return;
        }
        TYPES.put(type.gameId(), type);
    }

    public static synchronized Optional<MapGamemodeType> get(String gameId) {
        String normalized = gameId == null ? "" : gameId.trim().toLowerCase(java.util.Locale.ROOT);
        return Optional.ofNullable(TYPES.get(normalized));
    }

    public static synchronized Collection<MapGamemodeType> all() {
        return List.copyOf(TYPES.values());
    }
}
