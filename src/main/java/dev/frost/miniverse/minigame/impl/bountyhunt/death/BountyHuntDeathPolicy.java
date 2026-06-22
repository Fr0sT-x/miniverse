package dev.frost.miniverse.minigame.impl.bountyhunt.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class BountyHuntDeathPolicy implements DeathPolicy {
    @Override
    public void execute(ServerPlayerEntity player, DeathContext context) {
        // The item drops are handled by vanilla (keepInventory=false).
        // The game scoring and target rotation logic is handled in BountyHuntMinigame.processPlayerDeath
        // via the BountyHuntDeathCallbacks hook, to keep the game logic strongly encapsulated.
    }

    @Override
    public boolean interceptsRespawn() {
        return true;
    }
}
