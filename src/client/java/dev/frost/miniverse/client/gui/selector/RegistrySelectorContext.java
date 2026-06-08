package dev.frost.miniverse.client.gui.selector;

import java.util.Set;
import java.util.function.Consumer;

public record RegistrySelectorContext<T>(
    String registryType,
    String title,
    SelectionMode selectionMode,
    RegistrySelectorState state,
    Consumer<RegistrySelectionResult<T>> callback,
    String presetNamespace,
    Set<T> initialSelection
) {
    public enum SelectionMode {
        SINGLE,
        MULTI
    }
}
