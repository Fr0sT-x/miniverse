package dev.frost.miniverse.minigame.impl.murdermystery;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

public class MurderMysteryGameEvents {
    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        // TODO: Migrate MurderMystery to EntityInteractAware (tracked in DECISIONS.md)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
            if (runtime != null && runtime.minigame() instanceof MurderMysteryMinigame mm) {
                if (mm.getShopManager().handleInteract(serverPlayer, entity)) {
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });
    }
}
