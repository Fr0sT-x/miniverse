package dev.frost.miniverse.mixin.protection;

import dev.frost.miniverse.minigame.core.protection.MapProtectionManager;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonHandler.class)
public abstract class PistonHandlerMixin {

    @Shadow @Final private World world;

    @Shadow public abstract List<BlockPos> getMovedBlocks();

    @Shadow public abstract List<BlockPos> getBrokenBlocks();

    @Inject(method = "calculatePush", at = @At("RETURN"), cancellable = true)
    private void onCalculatePush(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || this.world.isClient || !(this.world instanceof ServerWorld serverWorld)) {
            return;
        }

        for (BlockPos pos : this.getMovedBlocks()) {
            if (MapProtectionManager.isProtected(serverWorld, pos)) {
                cir.setReturnValue(false);
                return;
            }
        }

        for (BlockPos pos : this.getBrokenBlocks()) {
            if (MapProtectionManager.isProtected(serverWorld, pos)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
