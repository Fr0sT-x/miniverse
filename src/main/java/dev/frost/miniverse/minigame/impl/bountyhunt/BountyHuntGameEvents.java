package dev.frost.miniverse.minigame.impl.bountyhunt;

import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public final class BountyHuntGameEvents {
    private BountyHuntGameEvents() {
    }

    public static void register() {
        BountyHuntSessionBootstrap.register();
        UseItemCallback.EVENT.register(BountyHuntGameEvents::onUseItem);
        ServerTickEvents.END_SERVER_TICK.register(BountyHuntGameEvents::onServerTick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(BountyHuntGameEvents::onAllowDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(BountyHuntGameEvents::onAfterDeath);
        ServerPlayerEvents.AFTER_RESPAWN.register(BountyHuntGameEvents::onAfterRespawn);
        ServerPlayerEvents.LEAVE.register(BountyHuntGameEvents::onPlayerLeave);
    }

    private static void onServerTick(net.minecraft.server.MinecraftServer server) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof BountyHuntMinigame bountyHunt) {
            bountyHunt.onServerTick(server);
        }
    }

    private static ActionResult onUseItem(net.minecraft.entity.player.PlayerEntity player, World world, Hand hand) {
        if (!(world instanceof ServerWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (!(active instanceof BountyHuntMinigame bountyHunt)) {
            return ActionResult.PASS;
        }

        if (!MinigameManager.getInstance().isParticipant(serverPlayer)) {
            return ActionResult.PASS;
        }

        if (!bountyHunt.isTrackerItem(serverPlayer.getStackInHand(hand))) {
            return ActionResult.PASS;
        }

        bountyHunt.cycleTrackerCooldown(serverPlayer);
        return ActionResult.SUCCESS;
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }

        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof BountyHuntMinigame bountyHunt && MinigameManager.getInstance().isParticipant(player)) {
            return !bountyHunt.shouldCancelDamage(player, source);
        }

        return true;
    }

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }

        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof BountyHuntMinigame bountyHunt && MinigameManager.getInstance().isParticipant(player)) {
            bountyHunt.handlePlayerDeath(player, source);
        }
    }

    private static void onAfterRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        MinigameManager manager = MinigameManager.getInstance();
        if (active instanceof BountyHuntMinigame bountyHunt && manager.isParticipant(oldPlayer)) {
            manager.replaceParticipant(oldPlayer, newPlayer);
            bountyHunt.handlePlayerRespawn(oldPlayer, newPlayer);
        }
    }

    private static void onPlayerLeave(ServerPlayerEntity player) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        MinigameManager manager = MinigameManager.getInstance();
        if (active instanceof BountyHuntMinigame bountyHunt && manager.isParticipant(player)) {
            manager.removeParticipant(player);
            bountyHunt.handlePlayerLeave(player);
        }
    }
}


