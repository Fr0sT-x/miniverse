package dev.frost.miniverse.minigame.impl.duels.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Duels death policy: cleans up the dead player and hands control to the framework
 * (which immediately puts them into SPECTATOR mode). The round system's startRound()
 * handles bringing them back to life at the start of the next round.
 */
public class DuelsDeathPolicy implements DeathPolicy {

    @Override
    public void execute(ServerPlayerEntity victim, DeathContext context) {
        victim.setHealth(20.0f);
        victim.clearStatusEffects();
        victim.getInventory().clear();
    }

    @Override
    public boolean interceptsRespawn() {
        // true = bypass the vanilla death screen; the framework will switch the player
        // to SPECTATOR mode directly and teleport them to the death location.
        return true;
    }
}
