package dev.frost.miniverse.map.editor;

public enum MarkerType {
    POINT,
    REGION,
    MULTI_POINT;

    public static MarkerType fromName(String value) {
        if (value == null || value.isBlank()) {
            return POINT;
        }
        try {
            return MarkerType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return POINT;
        }
    }
}
