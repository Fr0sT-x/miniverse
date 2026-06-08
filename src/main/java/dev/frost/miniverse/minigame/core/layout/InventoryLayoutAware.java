package dev.frost.miniverse.minigame.core.layout;

/**
 * Opt-in contract for minigames that support persistent player hotbar layouts.
 */
public interface InventoryLayoutAware {
    String inventoryLayoutGamemodeId();

    default String inventoryLayoutProfileId() {
        return "default";
    }
}
