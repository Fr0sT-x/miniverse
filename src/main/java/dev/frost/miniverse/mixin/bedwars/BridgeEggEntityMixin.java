package dev.frost.miniverse.mixin.bedwars;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.protection.MapProtectionTracker;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMinigame;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts egg collision for the BedWars bridge-egg mechanic.
 *
 * Two behaviours are enforced for any egg tracked as a bridge egg:
 *   1. No chickens are ever spawned — vanilla EggEntity.onCollision always tries
 *      to spawn chickens on hit, which is undesirable for the utility item.
 *   2. Collisions with player-placed blocks are silently ignored — the egg
 *      must not self-terminate when its own bridge trail grows into its path.
 *      Collisions with protected map blocks still stop the egg (but no chickens).
 */
@Mixin(EggEntity.class)
public class BridgeEggEntityMixin {

    @Inject(method = "onCollision", at = @At("HEAD"), cancellable = true)
    private void onBridgeEggCollision(HitResult hitResult, CallbackInfo ci) {
        EggEntity egg = (EggEntity) (Object) this;
        if (egg.getWorld().isClient) {
            return;
        }

        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime == null) {
            return;
        }
        if (!(runtime.minigame() instanceof BedwarsMinigame bedwars)) {
            return;
        }
        if (!bedwars.isBridgeEgg(egg.getUuid())) {
            return;
        }

        // This is a bridge egg — chickens must never spawn regardless of hit type.
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = ((BlockHitResult) hitResult).getBlockPos();
            MapProtectionTracker tracker = runtime.context().protectionTracker();
            if (tracker.isPlacedBlock(hitPos)) {
                // Hit one of its own (or another player's) placed blocks — keep flying.
                ci.cancel();
                return;
            }
        }

        // Hit a protected map block or an entity — stop the egg but produce no chickens.
        egg.discard();
        ci.cancel();
    }
}
