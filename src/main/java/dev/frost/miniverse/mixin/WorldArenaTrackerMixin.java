package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.arena.ArenaTracker;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldArenaTrackerMixin {

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("HEAD"))
    private void onSetBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;
        if (world instanceof ServerWorld serverWorld) {
            ArenaTracker.getManager(serverWorld).ifPresent(manager -> {
                BlockState oldState = serverWorld.getBlockState(pos);
                BlockEntity oldEntity = serverWorld.getBlockEntity(pos);
                manager.onBlockChanged(pos, oldState, oldEntity);
            });
        }
    }
}
