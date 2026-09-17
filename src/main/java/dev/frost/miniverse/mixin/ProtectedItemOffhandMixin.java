package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.core.item.ProtectedItemFeedback;
import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ProtectedItemOffhandMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void miniverse$blockProtectedOffhandSwap(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (packet.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            if (ProtectedItemService.getInstance().shouldCancelOffhandSwap(this.player)) {
                ProtectedItemFeedback.sendRuleBlockedMessage(this.player);
                this.player.playerScreenHandler.sendContentUpdates();
                ci.cancel();
            }
        }
    }
}
