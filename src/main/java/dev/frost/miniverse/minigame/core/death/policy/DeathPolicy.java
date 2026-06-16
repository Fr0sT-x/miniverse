package dev.frost.miniverse.minigame.core.death.policy;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import net.minecraft.server.network.ServerPlayerEntity;

public interface DeathPolicy {
    /**
     * Executes the initial death behavior (e.g. updating stats, handling inventory drops).
     * 
     * Called before gamemode change and respawn packet. Implementations must leave 
     * the player in a valid state for spectator transition (e.g. restoring health to 20f).
     *
     * @param player  the player who died
     * @param context the context of the death
     */
    void execute(ServerPlayerEntity player, DeathContext context);

    /**
     * @return true if this policy bypasses the vanilla death screen and respawn flow
     */
    boolean interceptsRespawn();
}
