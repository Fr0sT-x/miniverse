package dev.frost.miniverse.mixin.protection;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.session.BackendLaunchMode;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void onBlockPlaced(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (!cir.getReturnValue().isAccepted()) {
            return;
        }
        
        if (context.getWorld().isClient) {
            return;
        }

        if (SessionRuntimeConfig.getLaunchMode() == BackendLaunchMode.MAP_EDITOR) {
            return;
        }

        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime == null || !runtime.state().isActive()) {
            return;
        }

        BlockPos pos = context.getBlockPos();
        runtime.context().protectionTracker().addPlacedBlock(pos);
    }
}
