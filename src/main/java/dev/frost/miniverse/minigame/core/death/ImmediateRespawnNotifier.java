package dev.frost.miniverse.minigame.core.death;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Interface that MUST be implemented by any Minigame utilizing DO_IMMEDIATE_RESPAWN = true.
 * Used to enforce standardizing title and subtitle messaging for deaths.
 */
public interface ImmediateRespawnNotifier {
    /**
     * @return The title to display to the killed player.
     */
    Text getDeathTitle(ServerPlayerEntity victim, DamageSource source);

    /**
     * @param ticksRemaining Ticks left until respawn occurs.
     * @return The subtitle to display to the killed player.
     */
    Text getDeathSubtitle(ServerPlayerEntity victim, DamageSource source, int ticksRemaining);
}
