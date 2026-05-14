package dev.frost.miniverse.minigame.core.spectator;

import net.minecraft.entity.Entity;

import java.util.List;

@FunctionalInterface
public interface SpectatorTargetProvider {
    List<Entity> findTargets(SpectatorContext context);
}

