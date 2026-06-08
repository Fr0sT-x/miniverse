package dev.frost.miniverse.client.gui.selector;

import java.util.Set;

public record RegistrySelectionResult<T>(
    Set<T> selectedEntries,
    boolean inverted
) {
}
