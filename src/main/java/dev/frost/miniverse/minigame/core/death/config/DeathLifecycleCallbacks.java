package dev.frost.miniverse.minigame.core.death.config;

import dev.frost.miniverse.minigame.core.death.CancellationReason;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.DeathState;
import net.minecraft.server.network.ServerPlayerEntity;

public interface DeathLifecycleCallbacks {
    default void onDeathStateChanged(ServerPlayerEntity player, DeathContext context, DeathState previousState, DeathState currentState) {}
    default void onDeath(ServerPlayerEntity player, DeathContext context) {}
    default void onDeathProcessed(ServerPlayerEntity player, DeathContext context) {}
    default void onSpectatorEnter(ServerPlayerEntity player, DeathContext context) {}
    default void onSpectatorTargetChanged(ServerPlayerEntity player, DeathContext context) {}
    default void onSpectatorExit(ServerPlayerEntity player, DeathContext context) {}
    default void onRespawnBegin(ServerPlayerEntity player, DeathContext context) {}
    default void onRespawnComplete(ServerPlayerEntity player, DeathContext context) {}
    default void onDeathFlowCancelled(ServerPlayerEntity player, DeathContext context, CancellationReason reason) {}
}
