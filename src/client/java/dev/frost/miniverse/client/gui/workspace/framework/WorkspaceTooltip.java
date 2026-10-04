package dev.frost.miniverse.client.gui.workspace.framework;

import java.util.Objects;
import java.util.function.Supplier;

@FunctionalInterface
public interface WorkspaceTooltip {
    String resolve();

    static WorkspaceTooltip of(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Tooltip text cannot be null or empty.");
        }
        return () -> text;
    }

    static WorkspaceTooltip toggle(Supplier<Boolean> stateSupplier, String onText, String offText) {
        Objects.requireNonNull(stateSupplier, "State supplier cannot be null");
        if (onText == null || onText.isBlank()) {
            throw new IllegalArgumentException("Toggle ON tooltip text cannot be null or empty.");
        }
        if (offText == null || offText.isBlank()) {
            throw new IllegalArgumentException("Toggle OFF tooltip text cannot be null or empty.");
        }
        return () -> stateSupplier.get() ? onText : offText;
    }

    static WorkspaceTooltip cycle(Supplier<Integer> indexSupplier, String... stateTooltips) {
        Objects.requireNonNull(indexSupplier, "Index supplier cannot be null");
        if (stateTooltips == null || stateTooltips.length == 0) {
            throw new IllegalArgumentException("Cycle tooltips array cannot be null or empty.");
        }
        for (int i = 0; i < stateTooltips.length; i++) {
            if (stateTooltips[i] == null || stateTooltips[i].isBlank()) {
                throw new IllegalArgumentException("Cycle tooltip at index " + i + " cannot be null or blank.");
            }
        }
        return () -> {
            int idx = Math.clamp(indexSupplier.get(), 0, stateTooltips.length - 1);
            return stateTooltips[idx];
        };
    }

    static WorkspaceTooltip dynamic(Supplier<String> dynamicSupplier) {
        Objects.requireNonNull(dynamicSupplier, "Dynamic supplier cannot be null");
        return () -> {
            String result = dynamicSupplier.get();
            return (result != null && !result.isBlank()) ? result : "No description available.";
        };
    }
}
