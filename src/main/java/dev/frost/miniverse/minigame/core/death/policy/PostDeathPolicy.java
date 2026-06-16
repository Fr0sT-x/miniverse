package dev.frost.miniverse.minigame.core.death.policy;

import dev.frost.miniverse.minigame.core.death.CancellationReason;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PostDeathPolicy {
    /**
     * Starts the post-death phase (e.g. respawn timers or condition listeners).
     *
     * @param player  the player who died
     * @param context the context of the death
     */
    void start(ServerPlayerEntity player, DeathContext context);

    /**
     * Cancels any active timers or listeners associated with this policy.
     *
     * @param reason the reason for cancellation
     */
    void cancel(CancellationReason reason);

    /**
     * Called each server tick while this policy is active.
     * Provided server instance may be used for player lookups or scheduling.
     * Default implementation is a no-op.
     *
     * @param server the server instance
     */
    default void tick(net.minecraft.server.MinecraftServer server) {
    }
}
