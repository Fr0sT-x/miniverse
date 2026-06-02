package dev.frost.miniverse.map;

import java.util.ArrayList;
import java.util.List;

public record MapValidationResult(List<String> errors, List<String> warnings) {
    public MapValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static MapValidationResult ok() {
        return new MapValidationResult(List.of(), List.of());
    }

    public boolean valid() {
        return this.errors.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public Builder error(String message) {
            if (message != null && !message.isBlank()) {
                this.errors.add(message);
            }
            return this;
        }

        public Builder warning(String message) {
            if (message != null && !message.isBlank()) {
                this.warnings.add(message);
            }
            return this;
        }

        public MapValidationResult build() {
            return new MapValidationResult(this.errors, this.warnings);
        }
    }
}
