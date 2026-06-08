package dev.frost.miniverse.mixin.protection;

import dev.frost.miniverse.minigame.core.protection.MapProtectionManager;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowableFluid.class)
public class FlowableFluidMixin {

    @Inject(method = "canFill", at = @At("HEAD"), cancellable = true)
    private void onCanFill(BlockView world, BlockPos pos, BlockState state, Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        if (world instanceof ServerWorld serverWorld) {
            if (MapProtectionManager.isProtected(serverWorld, pos)) {
                // If it's a protected block, don't let fluid fill/destroy it
                cir.setReturnValue(false);
            }
        }
    }
}
