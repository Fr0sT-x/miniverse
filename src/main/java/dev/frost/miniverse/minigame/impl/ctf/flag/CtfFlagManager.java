package dev.frost.miniverse.minigame.impl.ctf.flag;

import dev.frost.miniverse.minigame.core.visibility.TeamGlowVisibility;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagMapConfig;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagMinigame;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagSettings;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CtfFlagManager {
    private final CaptureTheFlagMinigame minigame;
    private final CaptureTheFlagSettings settings;
    private final CaptureTheFlagMapConfig mapConfig;
    private final Map<String, CtfFlagInstance> flags = new ConcurrentHashMap<>();
    private final Map<String, Integer> teamCaptures = new ConcurrentHashMap<>();

    public CtfFlagManager(CaptureTheFlagMinigame minigame, CaptureTheFlagSettings settings, CaptureTheFlagMapConfig mapConfig) {
        this.minigame = minigame;
        this.settings = settings;
        this.mapConfig = mapConfig;
    }

    public void init(ServerWorld world, Set<String> activeTeamIds) {
        this.flags.clear();
        this.teamCaptures.clear();

        for (Map.Entry<String, CaptureTheFlagMapConfig.CtfTeamConfig> entry : this.mapConfig.teams().entrySet()) {
            String teamId = entry.getKey();
            CaptureTheFlagMapConfig.CtfTeamConfig teamConfig = entry.getValue();
            if (teamConfig != null && teamConfig.flagPos != null) {
                CtfFlagInstance instance = new CtfFlagInstance(teamId, teamConfig.name, teamConfig.color, teamConfig.flagPos);
                instance.spawnAtBase(world);
                this.flags.put(teamId, instance);
                this.teamCaptures.put(teamId, 0);
            }
        }
    }

    public void tick(ServerWorld world) {
        // 1. Tick flags (particles, dropped countdowns, auto-returns)
        for (CtfFlagInstance flag : this.flags.values()) {
            boolean returned = flag.tick(world);
            if (returned) {
                this.minigame.broadcast(Text.literal("⏱ The " + flag.teamName() + " Flag was returned to base (timed out).")
                    .formatted(flag.teamColor(), Formatting.BOLD));
                world.playSound(null, flag.basePos().x, flag.basePos().y, flag.basePos().z,
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.5F, 0.8F);
                this.minigame.rebuildScoreboard();
                this.minigame.checkWinCondition();
            }
        }

        // 2. Check player interactions with flags (grabbing, recovering, capturing)
        Collection<ServerPlayerEntity> players = this.minigame.getOnlineParticipants();
        for (ServerPlayerEntity player : players) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            String playerTeamId = this.minigame.teamManager().teamId(player.getUuid());
            if (playerTeamId == null) {
                continue;
            }

            CtfFlagInstance carriedFlag = this.getCarriedFlag(player.getUuid()).orElse(null);

            // A. If carrying an enemy flag, apply carrier glowing and check for capture
            if (carriedFlag != null) {
                if (this.settings.carrierGlowing()) {
                    player.setGlowing(true);
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, false, false, false));
                    if (player.hasStatusEffect(StatusEffects.INVISIBILITY) && world.getServer().getTicks() % 20 == 0) {
                        player.sendMessage(Text.literal("⚠ Carrier Revealed: Flag outline pierces invisibility!").formatted(Formatting.GOLD), true);
                    }
                }

                CaptureTheFlagMapConfig.CtfTeamConfig playerTeamConfig = this.mapConfig.teams().get(playerTeamId);
                if (playerTeamConfig != null) {
                    BlockPos dropoffPos = playerTeamConfig.getEffectiveDropoffPos();
                    if (dropoffPos != null) {
                        double distSq = player.squaredDistanceTo(dropoffPos.getX() + 0.5, dropoffPos.getY() + 0.5, dropoffPos.getZ() + 0.5);
                        if (distSq <= 4.0) { // 2 block capture radius
                            this.attemptCapture(world, player, playerTeamId, carriedFlag);
                        }
                    }
                }
            } else {
                // B. If not carrying any flag, check if walking onto any flag (base or dropped)
                for (CtfFlagInstance flag : this.flags.values()) {
                    if (flag.state() == CtfFlagState.AT_BASE) {
                        // Enemy team steals flag from base
                        if (!flag.teamId().equals(playerTeamId)) {
                            double distSq = player.squaredDistanceTo(flag.basePos());
                            if (distSq <= 2.25) { // 1.5 block pickup radius
                                this.stealFlag(world, player, playerTeamId, flag);
                            }
                        }
                    } else if (flag.state() == CtfFlagState.DROPPED) {
                        double distSq = player.squaredDistanceTo(flag.currentPos());
                        if (distSq <= 2.25) {
                            if (flag.teamId().equals(playerTeamId)) {
                                // Friendly recovery: returns to base!
                                this.recoverDroppedFlag(world, player, flag);
                            } else {
                                // Enemy re-pickup
                                this.rePickupDroppedFlag(world, player, playerTeamId, flag);
                            }
                        }
                    }
                }
            }
        }
    }

    private void stealFlag(ServerWorld world, ServerPlayerEntity player, String playerTeamId, CtfFlagInstance flag) {
        flag.attachToCarrier(player);
        if (this.settings.carrierGlowing()) {
            player.setGlowing(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, false, false, false));
            TeamGlowVisibility.resyncAll(world.getServer());
        }

        // Sound effects
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 2.0F, 1.0F);

        // Subtitles and Title
        this.broadcastTitle(Text.literal("FLAG STOLEN!").formatted(Formatting.RED, Formatting.BOLD),
            Text.literal(player.getName().getString() + " took the " + flag.teamName() + " Flag!").formatted(flag.teamColor()));

        this.minigame.broadcast(Text.literal("🚩 " + player.getName().getString() + " stole the " + flag.teamName() + " Flag!")
            .formatted(flag.teamColor(), Formatting.BOLD));

        // Economy reward for grabbing flag
        this.minigame.getEconomyManager().rewardFlagGrab(player);
        this.minigame.rebuildScoreboard();
    }

    private void recoverDroppedFlag(ServerWorld world, ServerPlayerEntity player, CtfFlagInstance flag) {
        flag.returnToBase(world);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.5F, 1.2F);

        this.minigame.broadcast(Text.literal("✔ " + player.getName().getString() + " recovered the " + flag.teamName() + " Flag!")
            .formatted(flag.teamColor(), Formatting.BOLD));

        this.minigame.rebuildScoreboard();
        this.minigame.checkWinCondition();
    }

    private void rePickupDroppedFlag(ServerWorld world, ServerPlayerEntity player, String playerTeamId, CtfFlagInstance flag) {
        flag.attachToCarrier(player);
        if (this.settings.carrierGlowing()) {
            player.setGlowing(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, false, false, false));
            TeamGlowVisibility.resyncAll(world.getServer());
        }

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 2.0F, 1.0F);

        this.minigame.broadcast(Text.literal("🚩 " + player.getName().getString() + " picked up the dropped " + flag.teamName() + " Flag!")
            .formatted(flag.teamColor(), Formatting.BOLD));

        this.minigame.rebuildScoreboard();
    }

    private void attemptCapture(ServerWorld world, ServerPlayerEntity player, String playerTeamId, CtfFlagInstance carriedFlag) {
        // Classic Rule: Check if friendly flag is at base
        if (this.settings.requireOwnFlagAtBase()) {
            CtfFlagInstance friendlyFlag = this.flags.get(playerTeamId);
            if (friendlyFlag != null && friendlyFlag.state() != CtfFlagState.AT_BASE) {
                player.sendMessage(Text.literal("⚠ Your team's flag must be safe at base to capture!").formatted(Formatting.RED), true);
                return;
            }
        }

        // Remove banner helmet from player and restore armor
        player.equipStack(EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY);
        player.setGlowing(false);
        player.removeStatusEffect(StatusEffects.GLOWING);
        TeamGlowVisibility.resyncAll(player.getServer());
        this.minigame.getEconomyManager().getUpgradeState(player.getUuid()).equipArmor(player, this.minigame.getPlayerTeamColor(player));

        int newCaptures = this.teamCaptures.compute(playerTeamId, (k, v) -> v == null ? 1 : v + 1);

        // Sound effects
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 2.0F, 1.0F);

        // Celebration fireworks
        spawnFirework(world, player.getPos(), this.flags.get(playerTeamId) != null ? this.flags.get(playerTeamId).teamColor() : Formatting.GOLD);

        CaptureTheFlagMapConfig.CtfTeamConfig capturerTeamConfig = this.mapConfig.teams().get(playerTeamId);
        String capturerTeamName = capturerTeamConfig != null ? capturerTeamConfig.name : playerTeamId;

        this.broadcastTitle(Text.literal("FLAG CAPTURED!").formatted(Formatting.GOLD, Formatting.BOLD),
            Text.literal(player.getName().getString() + " scored for " + capturerTeamName + "!").formatted(Formatting.YELLOW));

        this.minigame.broadcast(Text.literal("★ " + player.getName().getString() + " captured the " + carriedFlag.teamName() + " Flag for " + capturerTeamName + "!")
            .formatted(Formatting.GOLD, Formatting.BOLD));

        // Economy capture reward
        this.minigame.getEconomyManager().rewardFlagCapture(player);

        // Mode handling: Elimination vs Standard
        if (this.settings.eliminationMode()) {
            // Flag is permanently destroyed: opposing team loses respawns!
            carriedFlag.markCaptured(world, false);
            this.minigame.notifyFlagDestroyed(carriedFlag.teamId());
        } else {
            // Standard mode: Flag respawns at base after capture celebration
            carriedFlag.markCaptured(world, true);
        }

        this.minigame.rebuildScoreboard();
        this.minigame.checkWinCondition();
    }

    public void dropCarriedFlag(ServerPlayerEntity player, boolean voidKill) {
        if (player == null) return;
        CtfFlagInstance carried = this.getCarriedFlag(player.getUuid()).orElse(null);
        if (carried == null) return;

        player.equipStack(EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY);
        player.setGlowing(false);
        player.removeStatusEffect(StatusEffects.GLOWING);
        TeamGlowVisibility.resyncAll(player.getServer());
        this.minigame.getEconomyManager().getUpgradeState(player.getUuid()).equipArmor(player, this.minigame.getPlayerTeamColor(player));

        ServerWorld world = player.getServerWorld();
        if (voidKill) {
            carried.returnToBase(world);
            this.minigame.broadcast(Text.literal("☠ The " + carried.teamName() + " Flag fell into the void and returned to base.")
                .formatted(carried.teamColor()));
        } else {
            carried.dropOnGround(world, player.getPos(), this.settings.flagReturnDelaySeconds());
            this.minigame.broadcast(Text.literal("⚠ " + player.getName().getString() + " dropped the " + carried.teamName() + " Flag!")
                .formatted(carried.teamColor(), Formatting.BOLD));
        }
        this.minigame.rebuildScoreboard();
    }

    public boolean isCarrier(UUID playerUuid) {
        return this.getCarriedFlag(playerUuid).isPresent();
    }

    public Optional<CtfFlagInstance> getCarriedFlag(UUID playerUuid) {
        if (playerUuid == null) return Optional.empty();
        for (CtfFlagInstance flag : this.flags.values()) {
            if (flag.state() == CtfFlagState.CARRIED && playerUuid.equals(flag.carrierUuid())) {
                return Optional.of(flag);
            }
        }
        return Optional.empty();
    }

    public @Nullable CtfFlagInstance getFlag(String teamId) {
        return this.flags.get(teamId);
    }

    public Collection<CtfFlagInstance> allFlags() {
        return this.flags.values();
    }

    public int getCaptures(String teamId) {
        return this.teamCaptures.getOrDefault(teamId, 0);
    }

    public void clear(ServerWorld world) {
        for (CtfFlagInstance flag : this.flags.values()) {
            flag.clearStands(world);
        }
        this.flags.clear();
        this.teamCaptures.clear();
    }

    private void broadcastTitle(Text title, Text subtitle) {
        for (ServerPlayerEntity p : this.minigame.getOnlineParticipants()) {
            p.networkHandler.sendPacket(new TitleS2CPacket(title));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        }
    }

    private static void spawnFirework(ServerWorld world, Vec3d pos, Formatting color) {
        net.minecraft.entity.projectile.FireworkRocketEntity firework = new net.minecraft.entity.projectile.FireworkRocketEntity(
            world, pos.x, pos.y + 1.0, pos.z, net.minecraft.item.ItemStack.EMPTY);
        world.spawnEntity(firework);
    }
}
