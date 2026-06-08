package dev.frost.miniverse.minigame.core.event;

import dev.frost.miniverse.chat.ChatRouter;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.MinigameSessionStore;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.protection.MapProtectionManager;
import dev.frost.miniverse.minigame.core.region.RegionTriggerService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import net.minecraft.item.ItemStack;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
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
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (pausedFor(player)) return ActionResult.FAIL;
            if (player instanceof ServerPlayerEntity serverPlayer) {
                Minigame active = activeMinigame();
                if (active != null && !active.canBuild() && player.getStackInHand(hand).getItem() instanceof net.minecraft.item.BlockItem) {
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal("Building is disabled in this match.").formatted(net.minecraft.util.Formatting.RED), true);
                    return ActionResult.FAIL;
                }
                if (player.getStackInHand(hand).getItem() instanceof net.minecraft.item.BlockItem) {
                    net.minecraft.util.math.BlockPos targetPos = hitResult.getBlockPos().offset(hitResult.getSide());
                    net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(targetPos);
                    if (dev.frost.miniverse.minigame.core.region.RegionRestrictionService.getInstance().hasRestriction(box, dev.frost.miniverse.minigame.core.region.RegionRestriction.BUILD_DENIED)) {
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("You cannot build there").formatted(net.minecraft.util.Formatting.RED), false);
                        return ActionResult.FAIL;
                    }
                }
            }
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> pausedFor(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> pausedFor(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> pausedFor(player) ? ActionResult.FAIL : ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (pausedFor(player)) return false;
            if (player instanceof ServerPlayerEntity serverPlayer) {
                Minigame active = activeMinigame();
                if (active != null && !active.canBreakBlocks()) {
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal("Block breaking is disabled in this match.").formatted(net.minecraft.util.Formatting.RED), true);
                    return false;
                }
                net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(pos);
                if (dev.frost.miniverse.minigame.core.region.RegionRestrictionService.getInstance().hasRestriction(box, dev.frost.miniverse.minigame.core.region.RegionRestriction.BREAK_DENIED)) {
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal("You cannot break blocks here.").formatted(net.minecraft.util.Formatting.RED), true);
                    return false;
                }
                return MapProtectionManager.canBreak(serverPlayer, pos, true);
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            MapProtectionManager.onBlockBroken(pos);
        });
        ServerTickEvents.END_SERVER_TICK.register(MinigameEventRouter::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MinigameSessionStore.saveOnShutdown());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MinigameEventRouter::onAllowDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(MinigameEventRouter::onAfterDeath);
        ServerPlayConnectionEvents.JOIN.register(MinigameEventRouter::onPlayerJoin);
        ServerPlayerEvents.AFTER_RESPAWN.register(MinigameEventRouter::onAfterRespawn);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onPlayerLeave(handler.player));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(MinigameEventRouter::onAllowChatMessage);
    }

    private static void onServerTick(MinecraftServer server) {
        MinigameManager.getInstance().tickRuntimeClock(server);
        SessionBootstrapper.tick(server);
        MatchLifecycleController.getInstance().tick(server);
        SpectatorService.getInstance().tick(server);
        ProtectedItemService.getInstance().tick(server);
        MinigameSessionStore.tick(server);
        RegionTriggerService.getInstance().tick(server);
        if (MinigameManager.getInstance().getCurrentState() == GameState.PAUSED) {
            return;
        }
        Minigame active = activeMinigame();
        if (active instanceof ServerTickAware tickAware) {
            tickAware.onServerTick(server);
        }
    }

    private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        if (!(world instanceof ServerWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }

        if (pausedFor(serverPlayer)) {
            return TypedActionResult.fail(player.getStackInHand(hand));
        }

        Minigame active = activeMinigame();
        if (active instanceof ItemUseAware itemUseAware) {
            ActionResult result = itemUseAware.onUseItem(serverPlayer, world, hand);
            return new TypedActionResult<>(result, player.getStackInHand(hand));
        }

        return TypedActionResult.pass(player.getStackInHand(hand));
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }

        if (pausedFor(player)) {
            return false;
        }

        Minigame active = activeMinigame();
        if (active instanceof PlayerDamageAware damageAware) {
            return damageAware.allowDamage(player, source, amount);
        }

        return true;
    }

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        SpectatorService.getInstance().onEntityDeath(entity);
        if (entity instanceof ServerPlayerEntity player && pausedFor(player)) {
            return;
        }
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
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null && runtime.context().participants().contains(handler.player)) {
            if (MinigameSessionStore.restorePlayerState(runtime, handler.player)) {
                MinigameSessionStore.save(runtime, MinigameSessionStore.SaveReason.RECONNECT);
            }
        }
        MinigameManager.getInstance().applyPauseStateToParticipant(handler.player);
        SpectatorService.getInstance().onPlayerJoin(handler.player);
        ChatRouter.notifyPlayerIfMatchActive(handler.player);
    }

    private static void onAfterRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        SpectatorService.getInstance().onPlayerRespawn(oldPlayer, newPlayer, alive);
        ProtectedItemService.getInstance().onPlayerRespawn(oldPlayer, newPlayer, alive);
        if (pausedFor(newPlayer)) {
            return;
        }
        Minigame active = activeMinigame();
        if (active instanceof PlayerRespawnAware respawnAware) {
            respawnAware.onPlayerRespawn(oldPlayer, newPlayer, alive);
        }
    }

    private static void onPlayerLeave(ServerPlayerEntity player) {
        if (!pausedFor(player)) {
            boolean reconnectGraceStarted = MatchLifecycleController.getInstance().beginDisconnectGrace(player);
            MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
            if (runtime != null) {
                MinigameSessionStore.save(runtime, MinigameSessionStore.SaveReason.DISCONNECT);
            }
            if (reconnectGraceStarted) {
                SpectatorService.getInstance().onPlayerLeave(player);
                return;
            }
        }
        SpectatorService.getInstance().onPlayerLeave(player);
        if (pausedFor(player)) {
            MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
            if (runtime != null) {
                MinigameSessionStore.save(runtime, MinigameSessionStore.SaveReason.DISCONNECT);
            }
            return;
        }
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

    private static boolean pausedFor(PlayerEntity player) {
        return player instanceof ServerPlayerEntity serverPlayer
            && MinigameManager.getInstance().getCurrentState() == GameState.PAUSED
            && MinigameManager.getInstance().isParticipant(serverPlayer);
    }
}
