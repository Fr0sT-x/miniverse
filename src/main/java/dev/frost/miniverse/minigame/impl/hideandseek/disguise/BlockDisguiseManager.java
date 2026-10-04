package dev.frost.miniverse.minigame.impl.hideandseek.disguise;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlockDisguiseManager {
    public static class HiderState {
        public DisguiseType disguise;
        public boolean solidified = false;
        public Vec3d lastPosition;
        public Vec3d lockedCenter = null;
        public int sneakTicks = 0;
        public int settleTicks = 0;
        public BlockPos lockedBlockPos = null;
        public BlockState replacedOriginalState = null;

        public HiderState(DisguiseType disguise, Vec3d pos) {
            this.disguise = disguise;
            this.lastPosition = pos;
        }
    }

    private static final Set<UUID> SOLIDIFIED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> DISGUISED_PLAYERS = ConcurrentHashMap.newKeySet();

    public static boolean isHideAndSeekActive() {
        dev.frost.miniverse.minigame.core.Minigame active = dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getActiveMinigame();
        return active instanceof dev.frost.miniverse.minigame.impl.hideandseek.HideAndSeekMinigame;
    }

    public static boolean isPlayerSolidified(UUID uuid) {
        return isHideAndSeekActive() && uuid != null && SOLIDIFIED_PLAYERS.contains(uuid);
    }

    public static boolean isPlayerDisguised(UUID uuid) {
        return isHideAndSeekActive() && uuid != null && DISGUISED_PLAYERS.contains(uuid);
    }

    private final ServerWorld world;
    private final int solidifyRequiredTicks;
    private final Map<UUID, HiderState> hiders = new ConcurrentHashMap<>();
    private final Map<BlockPos, BlockState> placedBlocks = new ConcurrentHashMap<>();
    private List<DisguiseType> availableDisguises = DisguiseType.ALL;

    public BlockDisguiseManager(ServerWorld world, double solidifyDelaySeconds) {
        this.world = world;
        this.solidifyRequiredTicks = Math.max(20, (int) (solidifyDelaySeconds * 20));
    }

    public void setAvailableDisguises(List<DisguiseType> disguises) {
        if (disguises != null && !disguises.isEmpty()) {
            this.availableDisguises = new java.util.ArrayList<>(disguises);
        } else {
            this.availableDisguises = DisguiseType.ALL;
        }
    }

    public List<DisguiseType> getAvailableDisguises() {
        return this.availableDisguises != null && !this.availableDisguises.isEmpty() ? this.availableDisguises : DisguiseType.ALL;
    }

    public DisguiseType getDefaultDisguise() {
        List<DisguiseType> list = getAvailableDisguises();
        return !list.isEmpty() ? list.get(0) : DisguiseType.CRAFTING_TABLE;
    }

    public void applyDisguise(ServerPlayerEntity player, DisguiseType type) {
        this.removeDisguise(player);

        // Apply invisibility without particles
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, StatusEffectInstance.INFINITE, 0, false, false, false));

        HiderState state = new HiderState(type, player.getPos());
        this.hiders.put(player.getUuid(), state);
        DISGUISED_PLAYERS.add(player.getUuid());

        // Broadcast disguise to all clients so PlayerEntityRendererMixin renders the block
        String blockId = Registries.BLOCK.getId(type.blockState().getBlock()).toString();
        this.broadcastDisguise(player.getUuid(), blockId, true);
        player.calculateDimensions();

        // Auto switch client to third-person perspective
        ServerPlayNetworking.send(player, new NetworkConstants.DisguisePerspectivePayload(true));

        player.sendMessage(Text.literal("§aYou disguised as a §e" + type.displayName() + "§a! Hold §6SNEAK (3s) §ato solidify.§r"), false);
        player.playSoundToPlayer(SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.PLAYERS, 0.8F, 1.2F);
    }

    public void syncAllTo(ServerPlayerEntity player) {
        for (Map.Entry<UUID, HiderState> entry : this.hiders.entrySet()) {
            HiderState state = entry.getValue();
            String blockId = Registries.BLOCK.getId(state.disguise.blockState().getBlock()).toString();
            ServerPlayNetworking.send(player, new NetworkConstants.DisguiseSyncPayload(entry.getKey(), blockId, true));
            if (state.solidified) {
                ServerPlayNetworking.send(player, new NetworkConstants.SolidifySyncPayload(entry.getKey(), true));
            }
        }
    }

    public void tick(Map<UUID, ServerPlayerEntity> livePlayers) {
        for (Map.Entry<UUID, HiderState> entry : this.hiders.entrySet()) {
            UUID uuid = entry.getKey();
            HiderState state = entry.getValue();
            ServerPlayerEntity player = livePlayers.get(uuid);

            if (player == null || player.isDisconnected() || player.isSpectator()) {
                this.removeDisguiseByUuid(uuid);
                continue;
            }

            // Ensure invisibility stays refreshed
            if (!player.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, StatusEffectInstance.INFINITE, 0, false, false, false));
            }

            Vec3d currentPos = player.getPos();
            double distanceSq = currentPos.squaredDistanceTo(state.lastPosition);

            if (state.solidified) {
                if (state.settleTicks > 0) {
                    state.settleTicks--;
                } else if (state.lockedCenter != null) {
                    double horizontalDistSq = (currentPos.x - state.lockedCenter.x) * (currentPos.x - state.lockedCenter.x)
                                            + (currentPos.z - state.lockedCenter.z) * (currentPos.z - state.lockedCenter.z);
                    double verticalDist = Math.abs(currentPos.y - state.lockedCenter.y);

                    // Any intentional movement away from block center (> 0.25 blocks horizontally or > 0.35 blocks vertically) immediately unsolidifies
                    if (horizontalDistSq > 0.0625 || verticalDist > 0.35) {
                        this.unsolidify(player, state);
                    }
                }
                state.lastPosition = currentPos;
            } else {
                boolean moved = distanceSq > 0.005;

                // Sneak-to-solidify check: MUST be continuously sneaking, stationary, and on ground
                if (player.isSneaking() && !moved && player.isOnGround()) {
                    state.sneakTicks++;

                    int progressSegments = Math.min(10, (state.sneakTicks * 10) / this.solidifyRequiredTicks);
                    int emptySegments = 10 - progressSegments;
                    String bar = "■".repeat(progressSegments) + "□".repeat(emptySegments);
                    double secondsLeft = Math.max(0, (this.solidifyRequiredTicks - state.sneakTicks) / 20.0);

                    player.sendMessage(Text.literal("§eSolidifying: §a[" + bar + "] §6(" + String.format(java.util.Locale.US, "%.1f", secondsLeft) + "s) §7[Hold SNEAK]"), true);

                    if (state.sneakTicks % 10 == 0) {
                        player.playSoundToPlayer(SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.4F, 1.0F + (state.sneakTicks / (float) this.solidifyRequiredTicks) * 0.6F);
                    }

                    if (state.sneakTicks >= this.solidifyRequiredTicks) {
                        this.solidify(player, state);
                    }
                } else {
                    if (state.sneakTicks > 0) {
                        state.sneakTicks = 0;
                        player.sendMessage(Text.literal("§cSolidify cancelled (Moved or released sneak)").formatted(Formatting.RED), true);
                    }
                }
                state.lastPosition = currentPos;
            }
        }
    }

    private void solidify(ServerPlayerEntity player, HiderState state) {
        BlockPos floored = BlockPos.ofFloored(player.getX(), player.getY(), player.getZ());
        BlockState currentWorldState = this.world.getBlockState(floored);
        BlockPos targetPos = floored;

        if (!currentWorldState.isAir() && !currentWorldState.isReplaceable()) {
            BlockPos up = floored.up();
            BlockState upState = this.world.getBlockState(up);
            if (upState.isAir() || upState.isReplaceable()) {
                targetPos = up;
            }
        }

        if (this.placedBlocks.containsKey(targetPos)) {
            player.sendMessage(Text.literal("§cCannot solidify here (Space already occupied)!").formatted(Formatting.RED), true);
            state.sneakTicks = 0;
            return;
        }

        state.solidified = true;
        state.sneakTicks = 0;
        state.settleTicks = 2; // Brief 2-tick grace period for initial server position adjustment
        state.lockedBlockPos = targetPos;
        state.lockedCenter = new Vec3d(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        SOLIDIFIED_PLAYERS.add(player.getUuid());

        BlockState original = this.world.getBlockState(targetPos);
        state.replacedOriginalState = original;
        this.placedBlocks.put(targetPos, original);

        // Place physical solid block into the Minecraft world so Seekers cannot walk through it
        BlockState stateToPlace = state.disguise.blockState();
        if (stateToPlace.contains(Properties.HORIZONTAL_FACING)) {
            stateToPlace = stateToPlace.with(Properties.HORIZONTAL_FACING, player.getHorizontalFacing().getOpposite());
        } else if (stateToPlace.contains(Properties.FACING)) {
            stateToPlace = stateToPlace.with(Properties.FACING, player.getHorizontalFacing().getOpposite());
        } else if (stateToPlace.contains(Properties.AXIS)) {
            stateToPlace = stateToPlace.with(Properties.AXIS, player.getHorizontalFacing().getAxis());
        }

        this.world.setBlockState(targetPos, stateToPlace, Block.NOTIFY_ALL);

        // Align player directly inside the placed block
        player.teleport(this.world, targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch());
        state.lastPosition = player.getPos();

        // Expand player hitbox to 1.0x1.0x1.0 solid block
        player.calculateDimensions();

        // Broadcast solidify state to all clients
        this.broadcastSolidify(player.getUuid(), true);

        // Audio & visual feedback
        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.0F, 1.5F);
        this.world.playSound(null, targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0F, 0.9F);
        this.world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);

        player.sendMessage(Text.literal("§a✔ SOLIDIFIED! You are now a solid block. (Move to unlock)").formatted(Formatting.GREEN, Formatting.BOLD), true);
    }

    public void unsolidify(ServerPlayerEntity player, HiderState state) {
        if (state.lockedBlockPos != null) {
            BlockPos pos = state.lockedBlockPos;
            BlockState original = state.replacedOriginalState != null ? state.replacedOriginalState : Blocks.AIR.getDefaultState();
            this.world.setBlockState(pos, original, Block.NOTIFY_ALL);
            this.placedBlocks.remove(pos);
            state.lockedBlockPos = null;
            state.replacedOriginalState = null;
        }

        state.solidified = false;
        state.lockedCenter = null;
        state.sneakTicks = 0;
        state.settleTicks = 0;

        if (player != null) {
            SOLIDIFIED_PLAYERS.remove(player.getUuid());
            player.calculateDimensions();
            this.broadcastSolidify(player.getUuid(), false);
            player.sendMessage(Text.literal("§eUnlocked from grid. (Moving)").formatted(Formatting.YELLOW), true);
        }
    }

    public void unsolidifyAll() {
        for (Map.Entry<UUID, HiderState> entry : this.hiders.entrySet()) {
            HiderState state = entry.getValue();
            if (state.solidified) {
                ServerPlayerEntity p = this.world.getServer() != null ? this.world.getServer().getPlayerManager().getPlayer(entry.getKey()) : null;
                this.unsolidify(p, state);
            }
        }
    }

    public Optional<ServerPlayerEntity> findHiderAt(BlockPos pos, Map<UUID, ServerPlayerEntity> livePlayers) {
        // Match the attacked block against the actual disguise cell first. The
        // previous distance fallback could miss edge clicks and could also hit
        // a nearby disguise through an adjacent block.
        for (Map.Entry<UUID, HiderState> entry : this.hiders.entrySet()) {
            HiderState state = entry.getValue();
            if (state.solidified && pos.equals(state.lockedBlockPos)) {
                ServerPlayerEntity hider = livePlayers.get(entry.getKey());
                if (hider != null && !hider.isSpectator()) {
                    return Optional.of(hider);
                }
            }
        }
        return Optional.empty();
    }

    public boolean isSolidified(UUID uuid) {
        HiderState state = this.hiders.get(uuid);
        return state != null && state.solidified;
    }

    public DisguiseType getDisguise(UUID uuid) {
        HiderState state = this.hiders.get(uuid);
        return state != null ? state.disguise : getDefaultDisguise();
    }

    public void forceUnlock(UUID uuid) {
        HiderState state = this.hiders.get(uuid);
        if (state != null && state.solidified) {
            ServerPlayerEntity player = this.world.getServer().getPlayerManager().getPlayer(uuid);
            this.unsolidify(player, state);
        }
    }

    public void removeDisguise(ServerPlayerEntity player) {
        if (player == null) return;
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        ServerPlayNetworking.send(player, new NetworkConstants.DisguisePerspectivePayload(false));
        this.removeDisguiseByUuid(player.getUuid());
    }

    private void removeDisguiseByUuid(UUID uuid) {
        SOLIDIFIED_PLAYERS.remove(uuid);
        DISGUISED_PLAYERS.remove(uuid);
        this.broadcastDisguise(uuid, "", false);
        this.broadcastSolidify(uuid, false);

        HiderState state = this.hiders.remove(uuid);
        ServerPlayerEntity p = this.world.getServer() != null ? this.world.getServer().getPlayerManager().getPlayer(uuid) : null;
        if (state != null && p != null) {
            this.unsolidify(p, state);
        }
        if (p != null) {
            p.calculateDimensions();
        }
    }

    public void clearAll() {
        SOLIDIFIED_PLAYERS.clear();
        DISGUISED_PLAYERS.clear();

        // Rollback all placed blocks in the world to preserve map integrity
        for (Map.Entry<BlockPos, BlockState> entry : this.placedBlocks.entrySet()) {
            this.world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
        }
        this.placedBlocks.clear();

        for (Map.Entry<UUID, HiderState> entry : this.hiders.entrySet()) {
            UUID uuid = entry.getKey();
            this.broadcastDisguise(uuid, "", false);
            this.broadcastSolidify(uuid, false);

            ServerPlayerEntity p = this.world.getServer() != null ? this.world.getServer().getPlayerManager().getPlayer(uuid) : null;
            if (p != null) {
                p.removeStatusEffect(StatusEffects.INVISIBILITY);
                p.calculateDimensions();
                ServerPlayNetworking.send(p, new NetworkConstants.DisguisePerspectivePayload(false));
            }
        }
        this.hiders.clear();

        // Broadcast disguise reset payload to all players on the server and recalculate dimensions
        if (this.world.getServer() != null) {
            NetworkConstants.DisguiseResetPayload resetPayload = new NetworkConstants.DisguiseResetPayload("match_end");
            for (ServerPlayerEntity p : this.world.getServer().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, resetPayload);
                p.calculateDimensions();
            }
        }
    }

    private void broadcastDisguise(UUID uuid, String blockId, boolean active) {
        if (this.world.getServer() == null) return;
        NetworkConstants.DisguiseSyncPayload payload = new NetworkConstants.DisguiseSyncPayload(uuid, blockId, active);
        for (ServerPlayerEntity p : this.world.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    private void broadcastSolidify(UUID uuid, boolean solidified) {
        if (this.world.getServer() == null) return;
        NetworkConstants.SolidifySyncPayload payload = new NetworkConstants.SolidifySyncPayload(uuid, solidified);
        for (ServerPlayerEntity p : this.world.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
