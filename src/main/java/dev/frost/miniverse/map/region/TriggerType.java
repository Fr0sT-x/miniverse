package dev.frost.miniverse.map.region;

public enum TriggerType {
    PLAYER_ENTER,
    PLAYER_EXIT,
    PLAYER_STAY,
    ENTITY_ENTER,
    ENTITY_EXIT,
    BLOCK_ENTER;

    public static TriggerType fromName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TriggerType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
