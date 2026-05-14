package dev.frost.miniverse.minigame.core.spectator;

import net.minecraft.entity.Entity;

public interface SpectatorPolicy {
    SpectatorRestrictions restrictions();

    boolean isTargetAllowed(SpectatorContext context, Entity target);
}

