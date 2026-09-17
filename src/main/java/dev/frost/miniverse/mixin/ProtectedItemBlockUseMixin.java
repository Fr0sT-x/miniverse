package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.core.item.ProtectedItemTags;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ProtectedItemBlockUseMixin {
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void miniverse$preventPerkItemUseOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (ProtectedItemTags.hasType(stack, ProtectedItemTypes.ZOMBIES_PERK)
            || ProtectedItemTags.hasType(stack, ProtectedItemTypes.ZOMBIES_PLACEHOLDER)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
