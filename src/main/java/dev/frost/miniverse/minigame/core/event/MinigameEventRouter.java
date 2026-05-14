package dev.frost.miniverse.minigame.core.event;

import dev.frost.miniverse.chat.ChatRouter;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class MinigameEventRouter {
    private static boolean registered;

    private MinigameEventRouter() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        UseItemCallback.EVENT.register(MinigameEventRouter::onUseItem);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> true);
        ServerTickEvents.END_SERVER_TICK.register(MinigameEventRouter::onServerTick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MinigameEventRouter::onAllowDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(MinigameEventRouter::onAfterDeath);
        ServerPlayConnectionEvents.JOIN.register(MinigameEventRouter::onPlayerJoin);
        ServerPlayerEvents.AFTER_RESPAWN.register(MinigameEventRouter::onAfterRespawn);
        ServerPlayerEvents.LEAVE.register(MinigameEventRouter::onPlayerLeave);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(MinigameEventRouter::onAllowChatMessage);
    }

    private static void onServerTick(MinecraftServer server) {
        MinigameManager.getInstance().tickRuntimeClock(server);
        SessionBootstrapper.tick(server);
        MatchLifecycleController.getInstance().tick(server);
        SpectatorService.getInstance().tick(server);
        Minigame active = activeMinigame();
        if (active instanceof ServerTickAware tickAware) {
            tickAware.onServerTick(server);
        }
    }

    private static ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (!(world instanceof ServerWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        Minigame active = activeMinigame();
        if (active instanceof ItemUseAware itemUseAware) {
            return itemUseAware.onUseItem(serverPlayer, world, hand);
        }

        return ActionResult.PASS;
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }

        Minigame active = activeMinigame();
        if (active instanceof PlayerDamageAware damageAware) {
            return damageAware.allowDamage(player, source, amount);
        }

        return true;
    }

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        SpectatorService.getInstance().onEntityDeath(entity);
        Minigame active = activeMinigame();
        if (active instanceof EntityDeathAware deathAware) {
            deathAware.onEntityDeath(entity, source);
        }
    }

    private static void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        MinigameManager.getInstance().bindServer(server);
        Minigame active = activeMinigame();
        if (active instanceof PlayerJoinAware joinAware) {
            joinAware.onPlayerJoin(handler.player, server);
        }
        MatchLifecycleController.getInstance().onParticipantJoin(handler.player);
        SpectatorService.getInstance().onPlayerJoin(handler.player);
        ChatRouter.notifyPlayerIfMatchActive(handler.player);
    }

    private static void onAfterRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        SpectatorService.getInstance().onPlayerRespawn(oldPlayer, newPlayer, alive);
        Minigame active = activeMinigame();
        if (active instanceof PlayerRespawnAware respawnAware) {
            respawnAware.onPlayerRespawn(oldPlayer, newPlayer, alive);
        }
    }

    private static void onPlayerLeave(ServerPlayerEntity player) {
        SpectatorService.getInstance().onPlayerLeave(player);
        Minigame active = activeMinigame();
        if (active instanceof PlayerLeaveAware leaveAware) {
            leaveAware.onPlayerLeave(player);
        }
    }

    private static boolean onAllowChatMessage(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters parameters) {
        return !ChatRouter.handleChatMessage(message, player, parameters);
    }

    @Nullable
    private static Minigame activeMinigame() {
        return MinigameManager.getInstance().getActiveMinigame();
    }
}
