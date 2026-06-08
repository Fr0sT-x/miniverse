package dev.frost.miniverse.mixin.protection;

import dev.frost.miniverse.minigame.core.protection.MapProtectionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow @Final private World world;

    @Shadow public abstract List<BlockPos> getAffectedBlocks();

    @Inject(method = "affectWorld", at = @At("HEAD"))
    private void onAffectWorld(boolean particles, CallbackInfo ci) {
        if (!this.world.isClient && this.world instanceof ServerWorld serverWorld) {
            this.getAffectedBlocks().removeIf(pos -> MapProtectionManager.isProtected(serverWorld, pos));
        }
    }
}
