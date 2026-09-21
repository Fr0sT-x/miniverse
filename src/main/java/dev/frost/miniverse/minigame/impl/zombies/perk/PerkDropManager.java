package dev.frost.miniverse.minigame.impl.zombies.perk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesMapConfig;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesWindow;
import dev.frost.miniverse.minigame.impl.zombies.mob.ZombieEntityManager;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponItemHelper;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

public class PerkDropManager {
    public static final double PICKUP_RADIUS_SQ = 2.25; // 1.5 blocks
    public static final int DESPAWN_TICKS = 15 * 20; // 15 seconds

    public static class ActiveDrop {
        public final GlobalPerk perk;
        public final ArmorStandEntity stand;
        public int ticksRemaining;

        public ActiveDrop(GlobalPerk perk, ArmorStandEntity stand) {
            this.perk = perk;
            this.stand = stand;
            this.ticksRemaining = DESPAWN_TICKS;
        }
    }

    private final ServerWorld world;
    private final ZombiesMapConfig mapConfig;
    private final ZombieEntityManager mobManager;
    private final BiConsumer<ServerPlayerEntity, Integer> goldAwarder;
    private final Random random = new Random();

    private final List<ActiveDrop> activeDrops = new ArrayList<>();
    private int instantKillTicks = 0;
    private int doubleGoldTicks = 0;
    private final ServerBossBar instantKillBar = new ServerBossBar(
        Text.literal("⚡ INSTANT KILL").formatted(Formatting.RED, Formatting.BOLD),
        BossBar.Color.RED,
        BossBar.Style.PROGRESS
    );
    private final ServerBossBar doubleGoldBar = new ServerBossBar(
        Text.literal("✦ DOUBLE GOLD").formatted(Formatting.YELLOW, Formatting.BOLD),
        BossBar.Color.YELLOW,
        BossBar.Style.PROGRESS
    );

    public PerkDropManager(
        ServerWorld world,
        ZombiesMapConfig mapConfig,
        ZombieEntityManager mobManager,
        BiConsumer<ServerPlayerEntity, Integer> goldAwarder
    ) {
        this.world = world;
        this.mapConfig = mapConfig;
        this.mobManager = mobManager;
        this.goldAwarder = goldAwarder;
    }

    public boolean isInstantKillActive() {
        return this.instantKillTicks > 0;
    }

    public boolean isDoubleGoldActive() {
        return this.doubleGoldTicks > 0;
    }

    public int getInstantKillSeconds() {
        return Math.max(0, this.instantKillTicks / 20);
    }

    public int getDoubleGoldSeconds() {
        return Math.max(0, this.doubleGoldTicks / 20);
    }

    public void trySpawnDrop(Vec3d pos) {
        // 5% chance to drop a perk
        if (this.random.nextFloat() > 0.05f) return;

        GlobalPerk[] perks = GlobalPerk.values();
        GlobalPerk chosen = perks[this.random.nextInt(perks.length)];
        spawnDrop(pos, chosen);
    }

    public void spawnDrop(Vec3d pos, GlobalPerk perk) {
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, this.world);
        stand.setPosition(pos.x, pos.y, pos.z);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setNoGravity(true);
        stand.setCustomName(Text.literal("✦ " + perk.getDisplayName() + " ✦").formatted(perk.getColor(), Formatting.BOLD));
        stand.setCustomNameVisible(true);
        stand.equipStack(EquipmentSlot.HEAD, new ItemStack(perk.getIcon()));

        this.world.spawnEntity(stand);
        this.activeDrops.add(new ActiveDrop(perk, stand));
    }

    public void tick(List<ServerPlayerEntity> survivors) {
        if (this.instantKillTicks > 0) {
            this.instantKillTicks--;
            float pct = Math.clamp((float) this.instantKillTicks / GlobalPerk.INSTANT_KILL.getDurationTicks(), 0.0f, 1.0f);
            int sec = (this.instantKillTicks + 19) / 20;
            this.instantKillBar.setName(Text.literal("⚡ INSTANT KILL (" + sec + "s)").formatted(Formatting.RED, Formatting.BOLD));
            this.instantKillBar.setPercent(pct);
            for (ServerPlayerEntity p : survivors) {
                if (!p.isSpectator() && !this.instantKillBar.getPlayers().contains(p)) {
                    this.instantKillBar.addPlayer(p);
                }
            }
        } else {
            if (!this.instantKillBar.getPlayers().isEmpty()) {
                this.instantKillBar.clearPlayers();
            }
        }

        if (this.doubleGoldTicks > 0) {
            this.doubleGoldTicks--;
            float pct = Math.clamp((float) this.doubleGoldTicks / GlobalPerk.DOUBLE_GOLD.getDurationTicks(), 0.0f, 1.0f);
            int sec = (this.doubleGoldTicks + 19) / 20;
            this.doubleGoldBar.setName(Text.literal("✦ DOUBLE GOLD (" + sec + "s)").formatted(Formatting.YELLOW, Formatting.BOLD));
            this.doubleGoldBar.setPercent(pct);
            for (ServerPlayerEntity p : survivors) {
                if (!p.isSpectator() && !this.doubleGoldBar.getPlayers().contains(p)) {
                    this.doubleGoldBar.addPlayer(p);
                }
            }
        } else {
            if (!this.doubleGoldBar.getPlayers().isEmpty()) {
                this.doubleGoldBar.clearPlayers();
            }
        }

        if (this.activeDrops.isEmpty()) return;

        Iterator<ActiveDrop> it = this.activeDrops.iterator();
        while (it.hasNext()) {
            ActiveDrop drop = it.next();
            drop.ticksRemaining--;

            if (drop.ticksRemaining <= 0 || drop.stand.isRemoved()) {
                drop.stand.discard();
                it.remove();
                continue;
            }

            // Spin the armor stand
            drop.stand.setYaw(drop.stand.getYaw() + 6.0f);

            // Check pickup
            ServerPlayerEntity collector = null;
            for (ServerPlayerEntity p : survivors) {
                if (!p.isSpectator() && p.isAlive() && p.squaredDistanceTo(drop.stand) <= PICKUP_RADIUS_SQ) {
                    collector = p;
                    break;
                }
            }

            if (collector != null) {
                collectDrop(collector, drop.perk, survivors);
                drop.stand.discard();
                it.remove();
            }
        }
    }

    public void triggerPerk(ServerPlayerEntity collector, GlobalPerk perk, List<ServerPlayerEntity> survivors) {
        collectDrop(collector, perk, survivors);
    }

    private void collectDrop(ServerPlayerEntity collector, GlobalPerk perk, List<ServerPlayerEntity> survivors) {
        this.world.playSound(null, collector.getX(), collector.getY(), collector.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.2f, 1.2f);

        for (ServerPlayerEntity p : survivors) {
            p.sendMessage(Text.literal("✦ " + collector.getName().getString() + " activated ")
                .append(perk.toFormattedText())
                .append(Text.literal("!").formatted(Formatting.GREEN)), false);
        }

        switch (perk) {
            case INSTANT_KILL -> this.instantKillTicks = perk.getDurationTicks();
            case DOUBLE_GOLD -> this.doubleGoldTicks = perk.getDurationTicks();
            case MAX_AMMO -> {
                for (ServerPlayerEntity p : survivors) {
                    for (int i = 0; i < p.getInventory().size(); i++) {
                        ItemStack stack = p.getInventory().getStack(i);
                        WeaponType wt = WeaponItemHelper.getWeaponType(stack);
                        if (wt != null && !wt.getData().isMelee()) {
                            WeaponItemHelper.refillAmmo(stack, wt);
                        }
                    }
                    p.playSound(SoundEvents.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
                }
            }
            case CARPENTER -> {
                for (ZombiesWindow window : this.mapConfig.windows()) {
                    window.reset(this.world);
                }
                for (ServerPlayerEntity p : survivors) {
                    this.goldAwarder.accept(p, 200);
                    p.playSound(SoundEvents.BLOCK_ANVIL_USE, 0.8f, 1.4f);
                }
            }
            case NUKE -> {
                for (ZombieEntityManager.ActiveMob mob : this.mobManager.getActiveMobs()) {
                    if (mob.type != null && !mob.type.isBoss()) {
                        mob.entity.damage(this.world.getDamageSources().genericKill(), 9999.0f);
                    }
                }
                for (ServerPlayerEntity p : survivors) {
                    this.goldAwarder.accept(p, 400);
                    p.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.0f, 0.8f);
                }
            }
        }
    }

    public void cleanup() {
        this.instantKillBar.clearPlayers();
        this.doubleGoldBar.clearPlayers();
        for (ActiveDrop drop : this.activeDrops) {
            drop.stand.discard();
        }
        this.activeDrops.clear();
        this.instantKillTicks = 0;
        this.doubleGoldTicks = 0;
    }
}
