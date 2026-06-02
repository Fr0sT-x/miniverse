package dev.frost.miniverse.map;

public record MapGamemodeType(String gameId, String displayName, MapConfigValidator validator) {
    public MapGamemodeType {
        gameId = gameId == null ? "" : gameId.trim().toLowerCase(java.util.Locale.ROOT);
        displayName = displayName == null || displayName.isBlank() ? gameId : displayName.trim();
        validator = validator == null ? (map, config) -> MapValidationResult.ok() : validator;
    }
}
