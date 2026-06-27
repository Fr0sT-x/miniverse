package dev.frost.miniverse.minigame.core.event;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

public interface ItemUseOnBlockAware {
    ActionResult onUseBlock(ServerPlayerEntity player, World world, Hand hand, BlockHitResult hitResult);
}
