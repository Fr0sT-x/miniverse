package dev.frost.miniverse.client.gui.workspace.framework;

import dev.frost.miniverse.client.gui.ui.UiTheme;

public record ValidationResult(Type type, String message) {
    public enum Type {
        SUCCESS(UiTheme.SUCCESS),
        WARNING(UiTheme.WARNING),
        ERROR(UiTheme.ACCENT_RED),
        INFO(UiTheme.ACCENT_BLUE);

        private final int color;

        Type(int color) {
            this.color = color;
        }

        public int color() {
            return this.color;
        }
    }

    public static ValidationResult success(String message) {
        return new ValidationResult(Type.SUCCESS, message);
    }

    public static ValidationResult warning(String message) {
        return new ValidationResult(Type.WARNING, message);
    }

    public static ValidationResult error(String message) {
        return new ValidationResult(Type.ERROR, message);
    }

    public static ValidationResult info(String message) {
        return new ValidationResult(Type.INFO, message);
    }

    public boolean canStart() {
        return this.type == Type.SUCCESS || this.type == Type.WARNING;
    }
}
