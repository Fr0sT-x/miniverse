package dev.frost.miniverse.minigame.core.event;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public interface EntityInteractAware {
    /** Return SUCCESS to consume, PASS to continue vanilla handling. */
    ActionResult onEntityInteract(ServerPlayerEntity player, ServerWorld world, Hand hand, Entity entity);
}
