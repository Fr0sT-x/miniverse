package dev.frost.miniverse.minigame.impl.bridge;

import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class BridgeGameEvents {
    private BridgeGameEvents() {
    }

    public static void register() {
        BridgeSessionBootstrap.register();
        
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                Minigame active = MinigameManager.getInstance().getActiveMinigame();
                if (active instanceof BridgeMinigame bridge && bridge.getState() == GameState.PLAYING) {
                    if (player.getStackInHand(hand).getItem() instanceof BlockItem) {
                        BlockPos placePos = hitResult.getBlockPos().offset(hitResult.getSide());
                        if (bridge.isAboveHeightLimit(placePos)) {
                            serverPlayer.sendMessage(Text.literal("You cannot build above the height limit").formatted(Formatting.RED), false);
                            return ActionResult.FAIL;
                        }
                    }
                }
            }
            return ActionResult.PASS;
        });
    }
}
