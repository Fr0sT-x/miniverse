package dev.frost.miniverse.minigame.core.death.policy.impl.condition;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class CompositeRespawnCondition implements RespawnCondition {
    private final List<RespawnCondition> conditions;

    public CompositeRespawnCondition(List<RespawnCondition> conditions) {
        this.conditions = List.copyOf(conditions);
    }

    @Override
    public void register(ServerPlayerEntity victim, DeathContext context, Runnable onSatisfied) {
        if (this.conditions.isEmpty()) {
            onSatisfied.run();
            return;
        }

        Runnable checkAll = () -> {
            if (this.isSatisfied()) {
                onSatisfied.run();
            }
        };

        for (RespawnCondition condition : this.conditions) {
            condition.register(victim, context, checkAll);
        }
    }

    @Override
    public void unregister() {
        for (RespawnCondition condition : this.conditions) {
            condition.unregister();
        }
    }

    @Override
    public boolean isSatisfied() {
        for (RespawnCondition condition : this.conditions) {
            if (!condition.isSatisfied()) {
                return false;
            }
        }
        return true;
    }
}
