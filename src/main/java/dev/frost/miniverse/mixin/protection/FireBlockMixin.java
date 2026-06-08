package dev.frost.miniverse.mixin.protection;

import dev.frost.miniverse.minigame.core.protection.MapProtectionManager;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public class FireBlockMixin {
    @Inject(method = "trySpreadingFire", at = @At("HEAD"), cancellable = true)
    private void onTrySpreadingFire(World world, BlockPos pos, int spreadFactor, net.minecraft.util.math.random.Random random, int currentAge, CallbackInfo ci) {
        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            if (MapProtectionManager.isProtected(serverWorld, pos)) {
                ci.cancel();
            }
        }
    }
}
