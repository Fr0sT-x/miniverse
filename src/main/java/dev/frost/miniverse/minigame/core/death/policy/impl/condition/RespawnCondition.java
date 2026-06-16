package dev.frost.miniverse.minigame.core.death.policy.impl.condition;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import net.minecraft.server.network.ServerPlayerEntity;

public interface RespawnCondition {
    /**
     * Registers the condition.
     *
     * @param victim      the spectating player
     * @param context     the context of the death
     * @param onSatisfied the callback to execute when this condition transitions to satisfied
     */
    void register(ServerPlayerEntity victim, DeathContext context, Runnable onSatisfied);

    /**
     * Unregisters the condition, cleaning up any listeners.
     */
    void unregister();

    /**
     * @return true if the condition is currently met
     */
    boolean isSatisfied();
}
