package dev.frost.miniverse.minigame.core.death.policy.impl;

import dev.frost.miniverse.minigame.core.death.CancellationReason;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.condition.RespawnCondition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class ConditionalRespawnPolicy implements PostDeathPolicy {
    private final DeathLifecycleManager manager;
    private final RespawnCondition condition;
    private final MinecraftServer server;
    private UUID victimId;
    private volatile boolean respawnTriggered = false;

    public ConditionalRespawnPolicy(DeathLifecycleManager manager, RespawnCondition condition, MinecraftServer server) {
        this.manager = manager;
        this.condition = condition;
        this.server = server;
    }

    @Override
    public void start(ServerPlayerEntity victim, DeathContext context) {
        this.victimId = victim.getUuid();

        if (this.condition.isSatisfied()) {
            this.triggerRespawn();
            return;
        }

        this.condition.register(victim, context, () -> {
            if (this.condition.isSatisfied()) {
                this.triggerRespawn();
            }
        });
        
        // Failsafe in case it immediately satisfied during registration
        if (this.condition.isSatisfied()) {
            this.triggerRespawn();
        }
    }

    private void triggerRespawn() {
        if (this.respawnTriggered) {
            return;
        }
        this.respawnTriggered = true;
        this.condition.unregister();

        ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(this.victimId);
        if (player != null && !player.isDisconnected()) {
            this.manager.executeRespawn(player);
        }
    }

    @Override
    public void cancel(CancellationReason reason) {
        this.condition.unregister();
    }
}
