package dev.frost.miniverse.minigame.core.death.policy.impl;

import dev.frost.miniverse.minigame.core.death.CancellationReason;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class TimedRespawnPolicy implements PostDeathPolicy {
    private final DeathLifecycleManager manager;
    private final int delayTicks;
    
    private UUID victimId;
    private int ticksRemaining;
    private boolean active;

    public TimedRespawnPolicy(DeathLifecycleManager manager, int delayTicks) {
        this.manager = manager;
        this.delayTicks = delayTicks;
    }

    @Override
    public void start(ServerPlayerEntity victim, DeathContext context) {
        this.victimId = victim.getUuid();
        this.ticksRemaining = this.delayTicks;
        this.active = true;
        
        if (this.ticksRemaining <= 0) {
            this.active = false;
            this.manager.executeRespawn(victim);
        }
    }

    @Override
    public void cancel(CancellationReason reason) {
        this.active = false;
    }

    @Override
    public void tick(MinecraftServer server) {
        if (!this.active) {
            return;
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(this.victimId);
        // Defensive guard: handleDisconnect should cancel this policy before this state is reached
        if (player == null || player.isDisconnected()) {
            this.active = false;
            return;
        }

        this.ticksRemaining--;
        
        if (this.ticksRemaining <= 0) {
            this.active = false;
            this.manager.executeRespawn(player);
        }
    }
}
