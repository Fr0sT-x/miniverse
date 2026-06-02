package dev.frost.miniverse.session;

public enum BackendLaunchMode {
    NEW_SESSION,
    RESTORE_SESSION,
    INSPECTION_SESSION,
    MAP_EDITOR;

    public static BackendLaunchMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return NEW_SESSION;
        }
        try {
            return BackendLaunchMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return NEW_SESSION;
        }
    }
}
