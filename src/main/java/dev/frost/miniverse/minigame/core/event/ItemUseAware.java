package dev.frost.miniverse.minigame.core.event;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public interface ItemUseAware {
    ActionResult onUseItem(ServerPlayerEntity player, World world, Hand hand);
}
