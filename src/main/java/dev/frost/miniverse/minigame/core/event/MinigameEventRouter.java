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
    private final MatchLifecycleController matchLifecycleController;
    private boolean registered;

    public MinigameEventRouter(MatchLifecycleController matchLifecycleController) {
        this.matchLifecycleController = matchLifecycleController;
    }

    public synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        UseItemCallback.EVENT.register(this::onUseItem);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (this.pausedFor(player)) return ActionResult.FAIL;
            if (player instanceof ServerPlayerEntity serverPlayer) {
                Minigame active = this.activeMinigame();
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
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> this.pausedFor(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> this.pausedFor(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> this.pausedFor(player) ? ActionResult.FAIL : ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (this.pausedFor(player)) return false;
            if (player instanceof ServerPlayerEntity serverPlayer) {
                Minigame active = this.activeMinigame();
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
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMinigameSessionStore().saveOnShutdown());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::onAllowDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(this::onAfterDeath);
        ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);
        ServerPlayerEvents.AFTER_RESPAWN.register(this::onAfterRespawn);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> this.onPlayerLeave(handler.player));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(this::onAllowChatMessage);
    }

    private void onServerTick(MinecraftServer server) {
        MinigameManager.getInstance().tickRuntimeClock(server);
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getSessionBootstrapper().tick(server);
        this.matchLifecycleController.tick(server);
        SpectatorService.getInstance().tick(server);
        ProtectedItemService.getInstance().tick(server);
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMinigameSessionStore().tick(server);
        RegionTriggerService.getInstance().tick(server);
        if (MinigameManager.getInstance().getCurrentState() == GameState.PAUSED) {
            return;
        }
        Minigame active = this.activeMinigame();
        if (active instanceof ServerTickAware tickAware) {
            tickAware.onServerTick(server);
        }
    }

    private TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        if (!(world instanceof ServerWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }

        if (this.pausedFor(serverPlayer)) {
            return TypedActionResult.fail(player.getStackInHand(hand));
        }

        Minigame active = this.activeMinigame();
        if (active instanceof ItemUseAware itemUseAware) {
            ActionResult result = itemUseAware.onUseItem(serverPlayer, world, hand);
            return new TypedActionResult<>(result, player.getStackInHand(hand));
        }

        return TypedActionResult.pass(player.getStackInHand(hand));
    }

    private boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }

        if (this.pausedFor(player)) {
            return false;
        }

        Minigame active = this.activeMinigame();
        if (active instanceof PlayerDamageAware damageAware) {
            return damageAware.allowDamage(player, source, amount);
        }

        return true;
    }

    private void onAfterDeath(LivingEntity entity, DamageSource source) {
        SpectatorService.getInstance().onEntityDeath(entity);
        if (entity instanceof ServerPlayerEntity player && this.pausedFor(player)) {
            return;
        }
        Minigame active = this.activeMinigame();
        if (active instanceof EntityDeathAware deathAware) {
            deathAware.onEntityDeath(entity, source);
        }
    }

    private void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        MinigameManager.getInstance().bindServer(server);
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        Minigame active = this.activeMinigame();
        if (active instanceof PlayerJoinAware joinAware) {
            joinAware.onPlayerJoin(handler.player, server);
        }
        this.matchLifecycleController.onParticipantJoin(handler.player);
        if (runtime != null && runtime.context().roster().contains(handler.player)) {
            if (dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMinigameSessionStore().restorePlayerState(runtime, handler.player)) {
                dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMinigameSessionStore().save(runtime, MinigameSessionStore.SaveReason.RECONNECT);
            }
            if (active instanceof RosterAware rosterAware) {
                rosterAware.onRosterChanged(runtime.context().roster());
            }
        }
        MinigameManager.getInstance().applyPauseStateToParticipant(handler.player);
        SpectatorService.getInstance().onPlayerJoin(handler.player);
        ChatRouter.notifyPlayerIfMatchActive(handler.player);
    }

    private void onAfterRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        SpectatorService.getInstance().onPlayerRespawn(oldPlayer, newPlayer, alive);
        ProtectedItemService.getInstance().onPlayerRespawn(oldPlayer, newPlayer, alive);
        if (this.pausedFor(newPlayer)) {
            return;
        }
        Minigame active = this.activeMinigame();
        if (active instanceof PlayerRespawnAware respawnAware) {
            respawnAware.onPlayerRespawn(oldPlayer, newPlayer, alive);
        }
    }

    private void onPlayerLeave(ServerPlayerEntity player) {
        SpectatorService.getInstance().onPlayerLeave(player);
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMinigameSessionStore().save(runtime, MinigameSessionStore.SaveReason.DISCONNECT);
        }
        if (runtime != null && runtime.context().roster().contains(player)) {
            this.matchLifecycleController.onParticipantLeave(player);
            Minigame active = this.activeMinigame();
            if (active instanceof RosterAware rosterAware) {
                rosterAware.onRosterChanged(runtime.context().roster());
            }
            return;
        }
        if (this.pausedFor(player)) {
            return;
        }
        Minigame active = this.activeMinigame();
        if (active instanceof PlayerLeaveAware leaveAware) {
            leaveAware.onPlayerLeave(player);
        }
    }

    private boolean onAllowChatMessage(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters parameters) {
        return !ChatRouter.handleChatMessage(message, player, parameters);
    }

    @Nullable
    private Minigame activeMinigame() {
        return MinigameManager.getInstance().getActiveMinigame();
    }

    private boolean pausedFor(PlayerEntity player) {
        return player instanceof ServerPlayerEntity serverPlayer
            && MinigameManager.getInstance().getCurrentState() == GameState.PAUSED
            && MinigameManager.getInstance().isParticipant(serverPlayer);
    }
}
