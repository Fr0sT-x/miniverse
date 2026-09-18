package dev.frost.miniverse.minigame.impl.zombies.station;

import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesLuckyChest;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponItemHelper;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponType;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class ZombiesLuckyChestManager {
    public static final int ROLL_TICKS = 70; // 3.5 seconds of rolling animation
    public static final int CLAIM_TICKS = 400; // 20 seconds to claim

    public enum ChestState {
        IDLE,
        ROLLING,
        READY_TO_CLAIM
    }

    public static class ActiveChestSession {
        public final ZombiesLuckyChest chest;
        public final UUID userUuid;
        public final Vec3d center;
        public ChestState state;
        public int ticksRemaining;
        public ArmorStandEntity displayStand;
        public WeaponType finalWeapon;

        public ActiveChestSession(ZombiesLuckyChest chest, UUID userUuid, Vec3d center, ArmorStandEntity displayStand) {
            this.chest = chest;
            this.userUuid = userUuid;
            this.center = center;
            this.state = ChestState.ROLLING;
            this.ticksRemaining = ROLL_TICKS;
            this.displayStand = displayStand;
        }
    }

    private final ServerWorld world;
    private final List<ZombiesLuckyChest> chestLocations;
    private final BiPredicate<ServerPlayerEntity, Integer> goldSpender;
    private final Consumer<Text> broadcastCallback;
    private final ZombiesHologramManager hologramManager;
    private final BiPredicate<ServerPlayerEntity, dev.frost.miniverse.minigame.impl.zombies.perk.PlayerPerk> perkChecker;
    private final Random random = new Random();

    private final Map<String, ActiveChestSession> activeSessions = new HashMap<>();
    private dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponCustomConfig weaponConfig = dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponCustomConfig.defaults();

    public ZombiesLuckyChestManager(
        ServerWorld world,
        List<ZombiesLuckyChest> chestLocations,
        BiPredicate<ServerPlayerEntity, Integer> goldSpender,
        Consumer<Text> broadcastCallback,
        ZombiesHologramManager hologramManager,
        BiPredicate<ServerPlayerEntity, dev.frost.miniverse.minigame.impl.zombies.perk.PlayerPerk> perkChecker
    ) {
        this.world = world;
        this.chestLocations = chestLocations != null ? chestLocations : Collections.emptyList();
        this.goldSpender = goldSpender;
        this.broadcastCallback = broadcastCallback;
        this.hologramManager = hologramManager;
        this.perkChecker = perkChecker;
    }

    public void setWeaponConfig(dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponCustomConfig weaponConfig) {
        if (weaponConfig != null) {
            this.weaponConfig = weaponConfig;
        }
    }

    public void initHologram() {
        // Holograms are spawned via ZombiesHologramManager.spawnAll() for all lucky chests
    }

    public List<ZombiesLuckyChest> getChestLocations() {
        return this.chestLocations;
    }

    public boolean isLuckyChestPos(BlockPos pos) {
        if (pos == null) return false;
        for (ZombiesLuckyChest lc : this.chestLocations) {
            if (pos.equals(lc.getPos())) return true;
            if (pos.getSquaredDistance(lc.getPos()) <= 1.0 && this.world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                return true;
            }
        }
        return false;
    }

    public boolean handleInteract(ServerPlayerEntity player, BlockPos clickedPos) {
        ZombiesLuckyChest targetChest = null;
        // 1. Direct match with chest block or adjacent half of double chest
        for (ZombiesLuckyChest lc : this.chestLocations) {
            if (clickedPos.equals(lc.getPos())) {
                targetChest = lc;
                break;
            }
            if (clickedPos.getSquaredDistance(lc.getPos()) <= 1.0 && this.world.getBlockState(clickedPos).getBlock() instanceof ChestBlock) {
                targetChest = lc;
                break;
            }
        }

        // 2. Also check if clicked near any active roll display stand
        if (targetChest == null) {
            for (ActiveChestSession s : this.activeSessions.values()) {
                if (s.displayStand != null && player.squaredDistanceTo(s.displayStand) <= 9.0) {
                    targetChest = s.chest;
                    break;
                }
            }
        }

        if (targetChest == null) return false;

        ActiveChestSession session = this.activeSessions.get(targetChest.getId());

        // Ready to claim: grant weapon
        if (session != null && session.state == ChestState.READY_TO_CLAIM) {
            claimWeapon(player, session);
            return true;
        }

        // Currently rolling
        if (session != null && session.state == ChestState.ROLLING) {
            player.sendMessage(Text.literal("The Mystery Box is currently rolling!").formatted(Formatting.YELLOW), true);
            return true;
        }

        // Idle: check cost and start roll
        int cost = targetChest.getGold() > 0 ? targetChest.getGold() : 1000;
        if (!this.goldSpender.test(player, cost)) {
            player.sendMessage(Text.literal("Not enough gold! (" + cost + "g required)").formatted(Formatting.RED), true);
            this.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.8f, 0.5f);
            return true;
        }

        startRoll(player, targetChest);
        return true;
    }

    private void startRoll(ServerPlayerEntity player, ZombiesLuckyChest chest) {
        Vec3d center = ZombiesHologramManager.getChestCenter(this.world, chest.getPos());
        this.world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 1.0f);

        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, this.world);
        stand.setPosition(center.x, center.y - 0.8, center.z);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setNoGravity(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(Text.literal("Rolling...").formatted(Formatting.GOLD, Formatting.BOLD));
        this.world.spawnEntity(stand);

        setChestOpen(chest.getPos(), true);
        ActiveChestSession session = new ActiveChestSession(chest, player.getUuid(), center, stand);
        this.activeSessions.put(chest.getId(), session);
    }

    public void tick() {
        if (this.activeSessions.isEmpty()) return;

        List<WeaponType> chestWeapons = new ArrayList<>();
        for (WeaponType wt : WeaponType.values()) {
            if (this.weaponConfig.isAllowedInLuckyChest(wt)) {
                chestWeapons.add(wt);
            }
        }
        if (chestWeapons.isEmpty()) {
            chestWeapons.add(WeaponType.PISTOL);
        }

        Iterator<Map.Entry<String, ActiveChestSession>> it = this.activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            ActiveChestSession session = it.next().getValue();
            session.ticksRemaining--;

            if (session.displayStand == null || session.displayStand.isRemoved()) {
                it.remove();
                continue;
            }

            if (session.state == ChestState.ROLLING) {
                // Smooth float up and spin (lowered 1 block so weapon hovers just above chest)
                float progress = Math.clamp((float) (ROLL_TICKS - session.ticksRemaining) / ROLL_TICKS, 0.0f, 1.0f);
                session.displayStand.setPosition(
                    session.center.x,
                    session.center.y - 0.8 + (progress * 0.35),
                    session.center.z
                );
                session.displayStand.setYaw(session.displayStand.getYaw() + 15.0f);

                // Cycle weapon display every 4 ticks
                if (session.ticksRemaining % 4 == 0) {
                    WeaponType randomType = chestWeapons.get(this.random.nextInt(chestWeapons.size()));
                    session.displayStand.equipStack(EquipmentSlot.HEAD, new ItemStack(randomType.getData().item()));
                    session.displayStand.setCustomName(Text.literal("? " + randomType.getData().displayName() + " ?").formatted(Formatting.YELLOW));
                    float pitch = 1.0f + (progress * 0.8f);
                    this.world.playSound(null, session.center.x, session.center.y, session.center.z, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 0.8f, pitch);
                }

                if (session.ticksRemaining <= 0) {
                    // Pick final result
                    session.state = ChestState.READY_TO_CLAIM;
                    session.ticksRemaining = CLAIM_TICKS;
                    session.finalWeapon = chestWeapons.get(this.random.nextInt(chestWeapons.size()));
                    session.displayStand.equipStack(EquipmentSlot.HEAD, new ItemStack(session.finalWeapon.getData().item()));
                    session.displayStand.setPosition(session.center.x, session.center.y - 0.8 + 0.35, session.center.z);
                    int secLeft = (CLAIM_TICKS + 19) / 20;
                    session.displayStand.setCustomName(Text.literal("✦ " + session.finalWeapon.getData().displayName() + " ✦ (Click to Claim - " + secLeft + "s)").formatted(Formatting.GREEN, Formatting.BOLD));
                    this.world.playSound(null, session.center.x, session.center.y, session.center.z, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 1.0f, 1.4f);
                }
            } else if (session.state == ChestState.READY_TO_CLAIM) {
                // Hover gently and update countdown timer
                session.displayStand.setYaw(session.displayStand.getYaw() + 2.0f);
                if (session.ticksRemaining % 20 == 0 || session.ticksRemaining == CLAIM_TICKS - 1) {
                    int secLeft = Math.max(1, (session.ticksRemaining + 19) / 20);
                    if (session.finalWeapon != null) {
                        session.displayStand.setCustomName(Text.literal("✦ " + session.finalWeapon.getData().displayName() + " ✦ (Click to Claim - " + secLeft + "s)").formatted(Formatting.GREEN, Formatting.BOLD));
                    }
                }
                if (session.ticksRemaining <= 0) {
                    // Claim window expired
                    closeChest(session);
                    it.remove();
                }
            }
        }
    }

    private void claimWeapon(ServerPlayerEntity player, ActiveChestSession session) {
        if (session.finalWeapon != null) {
            boolean hasExtraWeapon = this.perkChecker != null && this.perkChecker.test(player, dev.frost.miniverse.minigame.impl.zombies.perk.PlayerPerk.EXTRA_WEAPON);
            int targetSlot = -1;
            int openSlot = dev.frost.miniverse.minigame.impl.zombies.item.ZombiesHotbarManager.getFirstOpenWeaponSlot(player, hasExtraWeapon);
            if (openSlot != -1) {
                targetSlot = openSlot;
            } else {
                int heldSlot = player.getInventory().selectedSlot;
                if (!dev.frost.miniverse.minigame.impl.zombies.item.ZombiesHotbarManager.isWeaponSlot(heldSlot, hasExtraWeapon)) {
                    String slotRange = hasExtraWeapon ? "2–4" : "2–3";
                    player.sendMessage(Text.literal("Weapon slots full! Select a weapon slot (" + slotRange + ") to replace.").formatted(Formatting.RED), true);
                    player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.5f);
                    return;
                }
                targetSlot = heldSlot;
            }

            ItemStack weaponStack = WeaponItemHelper.createWeaponStack(session.finalWeapon);
            player.getInventory().setStack(targetSlot, weaponStack);
            player.playerScreenHandler.sendContentUpdates();
            player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            player.sendMessage(Text.literal("✔ Obtained " + session.finalWeapon.getData().displayName() + "!").formatted(Formatting.GREEN, Formatting.BOLD), true);
            WeaponItemHelper.sendWeaponSpecSheet(player, session.finalWeapon, "You obtained " + session.finalWeapon.getData().displayName() + " from the Mystery Box!");
        }

        closeChest(session);
        this.activeSessions.remove(session.chest.getId());
    }

    private void closeChest(ActiveChestSession session) {
        if (session.displayStand != null && !session.displayStand.isRemoved()) {
            session.displayStand.discard();
        }
        setChestOpen(session.chest.getPos(), false);
        this.world.playSound(null, session.center.x, session.center.y, session.center.z, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    private void setChestOpen(BlockPos pos, boolean open) {
        if (this.world == null || pos == null) return;
        net.minecraft.block.BlockState state = this.world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            int viewerCount = open ? 1 : 0;
            this.world.addSyncedBlockEvent(pos, state.getBlock(), 1, viewerCount);
            net.minecraft.block.enums.ChestType type = state.get(ChestBlock.CHEST_TYPE);
            if (type != net.minecraft.block.enums.ChestType.SINGLE) {
                for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                    BlockPos adj = pos.offset(dir);
                    net.minecraft.block.BlockState adjState = this.world.getBlockState(adj);
                    if (adjState.getBlock() instanceof ChestBlock && adjState.get(ChestBlock.FACING) == state.get(ChestBlock.FACING)) {
                        this.world.addSyncedBlockEvent(adj, adjState.getBlock(), 1, viewerCount);
                    }
                }
            }
        }
    }

    public void cleanup() {
        for (ActiveChestSession s : this.activeSessions.values()) {
            if (s.displayStand != null && !s.displayStand.isRemoved()) {
                s.displayStand.discard();
            }
            setChestOpen(s.chest.getPos(), false);
        }
        this.activeSessions.clear();
    }
}
