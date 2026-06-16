package dev.frost.miniverse.minigame.core.death;

import org.jetbrains.annotations.Nullable;

/**
 * Interface for minigames that use the centralized Death Lifecycle Framework.
 */
public interface DeathAwareMinigame {
    /**
     * Gets the death lifecycle manager for this match.
     * @return the manager, or null if this minigame does not use the framework
     */
    @Nullable
    DeathLifecycleManager getDeathLifecycleManager();
}
