package dev.frost.miniverse.minigame.impl.manhunt;

import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame.ManhuntRole;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Server-side Fabric events for the Manhunt module.
 * Keeps input handling decoupled from the core game logic.
 */
public final class ManhuntGameEvents {
    private ManhuntGameEvents() {
    }

    public static void register() {
        ManhuntSessionBootstrap.register();
        UseItemCallback.EVENT.register(ManhuntGameEvents::onUseItem);
        ServerTickEvents.END_SERVER_TICK.register(ManhuntGameEvents::onServerTick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ManhuntGameEvents::onAllowDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(ManhuntGameEvents::onAfterDeath);
        ServerPlayerEvents.AFTER_RESPAWN.register(ManhuntGameEvents::onAfterRespawn);
        ServerPlayerEvents.LEAVE.register(ManhuntGameEvents::onPlayerLeave);
    }

    private static void onServerTick(net.minecraft.server.MinecraftServer server) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof ManhuntMinigame manhunt) {
            manhunt.onServerTick(server);
        }
    }

    private static ActionResult onUseItem(net.minecraft.entity.player.PlayerEntity player, World world, Hand hand) {
        if (!(world instanceof ServerWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        if (!player.getStackInHand(hand).isOf(Items.COMPASS)) {
            return ActionResult.PASS;
        }

        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (!(active instanceof ManhuntMinigame manhunt) || !manhunt.isGameActive()) {
            return ActionResult.PASS;
        }

        if (!MinigameManager.getInstance().isParticipant(serverPlayer) || manhunt.getPlayerRole(serverPlayer) != ManhuntRole.HUNTER) {
            return ActionResult.PASS;
        }

        ServerPlayerEntity target = manhunt.cycleHunterTrackingTarget(serverPlayer);
        if (target == null) {
            serverPlayer.sendMessage(Text.literal("No speedrunners are alive to track."), true);
            return ActionResult.SUCCESS;
        }

        serverPlayer.sendMessage(
            Text.literal("Tracking now: " + target.getName().getString() + " (right-click again to switch)")
                , true);
        return ActionResult.SUCCESS;
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }

        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof ManhuntMinigame manhunt && MinigameManager.getInstance().isParticipant(player)) {
            return !manhunt.shouldCancelDamage(player);
        }

        return true;
    }

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof ManhuntMinigame manhunt) {
            if (entity instanceof EnderDragonEntity) {
                manhunt.handleDragonDeath();
                return;
            }
        }

        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }

        if (active instanceof ManhuntMinigame manhunt && MinigameManager.getInstance().isParticipant(player)) {
            manhunt.onPlayerDeath(player);
        }
    }

    private static void onAfterRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        MinigameManager manager = MinigameManager.getInstance();
        if (active instanceof ManhuntMinigame manhunt && manager.isParticipant(oldPlayer)) {
            manager.replaceParticipant(oldPlayer, newPlayer);
            manhunt.handlePlayerRespawn(oldPlayer, newPlayer);
        }
    }

    private static void onPlayerLeave(ServerPlayerEntity player) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        MinigameManager manager = MinigameManager.getInstance();
        if (active instanceof ManhuntMinigame manhunt && manager.isParticipant(player)) {
            manager.removeParticipant(player);
            manhunt.handlePlayerLeave(player);
        }
    }
}
