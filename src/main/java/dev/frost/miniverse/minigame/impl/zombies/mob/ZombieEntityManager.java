package dev.frost.miniverse.minigame.impl.zombies.mob;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesWindow;
import dev.frost.miniverse.minigame.impl.zombies.mob.attack.BroodmotherAttack;
import dev.frost.miniverse.minigame.impl.zombies.mob.attack.FireTrailAttack;
import dev.frost.miniverse.minigame.impl.zombies.mob.attack.FireballAttack;
import dev.frost.miniverse.minigame.impl.zombies.mob.attack.GuardianLaserAttack;
import dev.frost.miniverse.minigame.impl.zombies.mob.attack.ZombieExplosion;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ZombieEntityManager {
    public static final String TAG_ZOMBIE_MOB = "zombies_mob";

    static {
        // Eagerly preload all mob attack and boss classes into JVM memory
        try {
            Class.forName(FireTrailAttack.class.getName());
            Class.forName(FireballAttack.class.getName());
            Class.forName(GuardianLaserAttack.class.getName());
            Class.forName(BroodmotherAttack.class.getName());
            Class.forName(ZombieExplosion.class.getName());
        } catch (Throwable ignored) {}
    }

    public static class ActiveMob {
        public final MobEntity entity;
        public final ZombieType type;
        public final ZombiesWindow window; // null if not spawned at window
        public int windowBreakTimer = 0;
        public int attackTimer = 0;
        public Vec3d lastTrackedPos = null;
        public int stuckTicks = 0;
        public Entity mount = null;

        public ActiveMob(MobEntity entity, ZombieType type, ZombiesWindow window) {
            this.entity = entity;
            this.type = type;
            this.window = window;
        }
    }

    private final ServerWorld world;
    private final Map<Integer, ActiveMob> activeMobs = new ConcurrentHashMap<>();
    private List<ZombiesWindow> windows = Collections.emptyList();
    private Consumer<ActiveMob> onMobKilledCallback;
    private Consumer<ActiveMob> stuckMobHandler;
    private long lastMobKilledTime = System.currentTimeMillis();
    private java.util.function.BooleanSupplier spawnQueueEmptySupplier = () -> false;

    public void setSpawnQueueEmptySupplier(java.util.function.BooleanSupplier supplier) {
        this.spawnQueueEmptySupplier = supplier;
    }

    public void resetLastKillTime() {
        this.lastMobKilledTime = System.currentTimeMillis();
        clearGlowingEffects();
    }

    public void clearGlowingEffects() {
        for (ActiveMob active : this.activeMobs.values()) {
            if (active.entity != null && active.entity.isAlive()) {
                active.entity.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.GLOWING);
            }
        }
    }

    public ZombieEntityManager(ServerWorld world) {
        this.world = world;
    }

    public void setWindows(List<ZombiesWindow> windows) {
        this.windows = windows != null ? windows : Collections.emptyList();
    }

    public void setOnMobKilledCallback(Consumer<ActiveMob> callback) {
        this.onMobKilledCallback = callback;
    }

    public void setStuckMobHandler(Consumer<ActiveMob> handler) {
        this.stuckMobHandler = handler;
    }

    public int getAliveMobCount() {
        return this.activeMobs.size();
    }

    public List<ActiveMob> getActiveMobs() {
        return new ArrayList<>(this.activeMobs.values());
    }

    public boolean isZombieMob(Entity entity) {
        return entity != null && this.activeMobs.containsKey(entity.getId());
    }

    public ActiveMob getActiveMob(Entity entity) {
        return entity != null ? this.activeMobs.get(entity.getId()) : null;
    }

    public ActiveMob spawnMob(ZombieType type, MapPosition spawnPos, ZombiesWindow window) {
        MobEntity mob = (MobEntity) type.getData().getEntityType().create(this.world);
        if (mob == null) return null;

        mob.refreshPositionAndAngles(spawnPos.x(), spawnPos.y(), spawnPos.z(), spawnPos.yaw(), spawnPos.pitch());
        mob.initialize(this.world, this.world.getLocalDifficulty(new BlockPos((int) spawnPos.x(), (int) spawnPos.y(), (int) spawnPos.z())), SpawnReason.EVENT, null);
        mob.addCommandTag(TAG_ZOMBIE_MOB);
        mob.setPersistent();

        ZombieMobData data = type.getData();

        // Attributes
        var healthAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(data.getHealth());
            mob.setHealth(data.getHealth());
        }

        var speedAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(data.getSpeed());
        }

        var followAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followAttr != null) {
            followAttr.setBaseValue(64.0);
        }

        // Baby flag
        if (data.isBaby() && mob instanceof ZombieEntity z) {
            z.setBaby(true);
        }

        // Equipment & 0% drop chances
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setEquipmentDropChance(slot, 0.0f);
        }
        for (Map.Entry<EquipmentSlot, ItemStack> entry : data.getEquipment().entrySet()) {
            mob.equipStack(entry.getKey(), entry.getValue().copy());
            mob.setEquipmentDropChance(entry.getKey(), 0.0f);
        }

        // Sunlight protection
        if (mob instanceof ZombieEntity && mob.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
            mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            mob.setEquipmentDropChance(EquipmentSlot.HEAD, 0.0f);
        }

        // Boss names
        if (type == ZombieType.BOMBIE) {
            mob.setCustomName(Text.literal("Bombie").formatted(Formatting.RED, Formatting.BOLD));
            mob.setCustomNameVisible(true);
        } else if (type == ZombieType.INFERNO) {
            mob.setCustomName(Text.literal("Inferno").formatted(Formatting.GOLD, Formatting.BOLD));
            mob.setCustomNameVisible(true);
        } else if (type == ZombieType.BROODMOTHER) {
            mob.setCustomName(Text.literal("Broodmother").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
            mob.setCustomNameVisible(true);
        }

        // Slime / Magma Cube size restriction: size 2 (medium) fits doorways/windows and prevents getting stuck
        if (mob instanceof SlimeEntity slime) {
            slime.setSize(2, true);
        }

        this.world.spawnEntity(mob);

        ActiveMob active = new ActiveMob(mob, type, window);
        active.lastTrackedPos = mob.getPos();
        if (mob.getVehicle() != null) {
            active.mount = mob.getVehicle();
            active.mount.addCommandTag(TAG_ZOMBIE_MOB);
        }
        this.activeMobs.put(mob.getId(), active);

        return active;
    }

    public void registerMinion(MobEntity minion, ZombieType type) {
        minion.addCommandTag(TAG_ZOMBIE_MOB);
        minion.setPersistent();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            minion.setEquipmentDropChance(slot, 0.0f);
        }
        ActiveMob active = new ActiveMob(minion, type, null);
        this.activeMobs.put(minion.getId(), active);
    }

    public void tick(List<ServerPlayerEntity> survivors, int tickCounter) {
        if (this.activeMobs.isEmpty()) return;

        List<ServerPlayerEntity> validTargets = survivors.stream()
            .filter(p -> !p.isSpectator() && p.isAlive())
            .toList();

        Iterator<Map.Entry<Integer, ActiveMob>> it = this.activeMobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ActiveMob> entry = it.next();
            ActiveMob active = entry.getValue();
            MobEntity mob = active.entity;

            if (mob == null || !mob.isAlive() || mob.isRemoved()) {
                if (active.mount != null && active.mount.isAlive()) {
                    active.mount.discard();
                }
                this.lastMobKilledTime = System.currentTimeMillis();
                clearGlowingEffects();
                if (this.onMobKilledCallback != null) {
                    this.onMobKilledCallback.accept(active);
                }
                it.remove();
                continue;
            }

            if (active.mount == null && mob.getVehicle() != null) {
                active.mount = mob.getVehicle();
                active.mount.addCommandTag(TAG_ZOMBIE_MOB);
            }

            active.attackTimer++;

            // Boss / Mob health in custom name
            if (active.type == ZombieType.BOMBIE) {
                mob.setCustomName(Text.literal("Bombie §c" + (int) mob.getHealth() + "❤").formatted(Formatting.RED, Formatting.BOLD));
            } else if (active.type == ZombieType.INFERNO) {
                mob.setCustomName(Text.literal("Inferno §6" + (int) mob.getHealth() + "❤").formatted(Formatting.GOLD, Formatting.BOLD));
            } else if (active.type == ZombieType.BROODMOTHER) {
                mob.setCustomName(Text.literal("Broodmother §d" + (int) mob.getHealth() + "❤").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
            }

            // Glowing outline only when <= 3 mobs remain, spawn queue is completely empty,
            // AND players haven't killed any mob for more than 20s
            boolean queueEmpty = this.spawnQueueEmptySupplier != null && this.spawnQueueEmptySupplier.getAsBoolean();
            long timeSinceLastKill = System.currentTimeMillis() - this.lastMobKilledTime;
            if (queueEmpty && this.activeMobs.size() <= 3 && timeSinceLastKill >= 20000L) {
                mob.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.GLOWING, 25, 0, false, false, false));
            }

            // Window Barricade Breaking
            ZombiesWindow winToBreak = active.window;
            if (winToBreak != null && !winToBreak.isCompletelyBroken(this.world)) {
                double distToSpawnSq = mob.squaredDistanceTo(
                    winToBreak.getSpawnPos().x(),
                    winToBreak.getSpawnPos().y(),
                    winToBreak.getSpawnPos().z()
                );
                if (distToSpawnSq <= 16.0) {
                    active.windowBreakTimer++;
                    if (active.windowBreakTimer % 15 == 0) {
                        BlockPos nearest = winToBreak.getNearestIntactBarricade(mob.getBlockPos(), this.world, 16.0);
                        if (nearest != null) {
                            winToBreak.playBarricadeHit(this.world, nearest);
                        }
                    }
                    if (active.windowBreakTimer >= active.type.getData().getBreakWindowTicks()) {
                        active.windowBreakTimer = 0;
                        winToBreak.breakOneSlab(this.world);
                    }
                    mob.getNavigation().stop();
                    continue;
                }
            } else if (winToBreak == null) {
                for (ZombiesWindow w : this.windows) {
                    if (!w.isCompletelyBroken(this.world)) {
                        BlockPos nearest = w.getNearestIntactBarricade(mob.getBlockPos(), this.world, 4.0);
                        if (nearest != null) {
                            active.windowBreakTimer++;
                            if (active.windowBreakTimer % 15 == 0) {
                                w.playBarricadeHit(this.world, nearest);
                            }
                            if (active.windowBreakTimer >= active.type.getData().getBreakWindowTicks()) {
                                active.windowBreakTimer = 0;
                                w.breakOneSlab(this.world);
                            }
                            mob.getNavigation().stop();
                            break;
                        }
                    }
                }
            }

            // Target nearest survivor
            ServerPlayerEntity target = findClosestSurvivor(mob, validTargets);
            if (target != null) {
                mob.setTarget(target);
                if (tickCounter % 20 == 0) {
                    mob.getNavigation().startMovingTo(target, 1.0);
                }
            }

            // Anti-Stuck Watchdog (Checked every 20 ticks / 1 second)
            if (tickCounter % 20 == 0) {
                if (active.lastTrackedPos == null) {
                    active.lastTrackedPos = mob.getPos();
                } else {
                    double distMovedSq = mob.getPos().squaredDistanceTo(active.lastTrackedPos);
                    boolean isNearTarget = target != null && mob.squaredDistanceTo(target) <= 6.25;

                    if (distMovedSq < 0.25 && !isNearTarget) {
                        active.stuckTicks += 20;

                        // Tier 1 (Stuck 4s / 80 ticks): Small vertical hop & repath
                        if (active.stuckTicks == 80) {
                            mob.setVelocity(mob.getVelocity().x * 0.5, 0.32, mob.getVelocity().z * 0.5);
                            mob.velocityModified = true;
                            mob.getNavigation().recalculatePath();
                        }
                        // Tier 2 (Stuck 8s / 160 ticks): Window unstick or forward nudge
                        else if (active.stuckTicks == 160) {
                            if (winToBreak != null && winToBreak.isCompletelyBroken(this.world)) {
                                var rb = winToBreak.getRepairZone();
                                if (rb != null) {
                                    double rx = (rb.min().x() + rb.max().x()) / 2.0 + 0.5;
                                    double ry = Math.min(rb.min().y(), rb.max().y()) + 0.1;
                                    double rz = (rb.min().z() + rb.max().z()) / 2.0 + 0.5;
                                    mob.teleport(this.world, rx, ry, rz, Set.of(), mob.getYaw(), mob.getPitch());
                                    this.world.spawnParticles(ParticleTypes.POOF, rx, ry + 0.5, rz, 8, 0.2, 0.2, 0.2, 0.02);
                                    mob.getNavigation().recalculatePath();
                                }
                            } else if (target != null) {
                                Vec3d dir = target.getPos().subtract(mob.getPos()).normalize().multiply(0.4);
                                mob.setVelocity(dir.x, 0.35, dir.z);
                                mob.velocityModified = true;
                                mob.getNavigation().recalculatePath();
                            }
                        }
                        // Tier 3 (Stuck 14s / 280 ticks): Fail-safe teleport / respawn
                        else if (active.stuckTicks >= 280) {
                            active.stuckTicks = 0;
                            if (this.stuckMobHandler != null) {
                                this.stuckMobHandler.accept(active);
                            } else if (target != null) {
                                Vec3d tp = target.getPos();
                                mob.teleport(this.world, tp.x, tp.y, tp.z, Set.of(), mob.getYaw(), mob.getPitch());
                                this.world.spawnParticles(ParticleTypes.PORTAL, tp.x, tp.y + 0.5, tp.z, 20, 0.5, 0.5, 0.5, 0.1);
                            }
                        }
                    } else {
                        active.stuckTicks = 0;
                        active.lastTrackedPos = mob.getPos();
                    }
                }
            }

            // Special attacks
            switch (active.type) {
                case LITTLE_BOMBIE -> {
                    if (target != null && mob.squaredDistanceTo(target) <= 2.25) {
                        ZombieExplosion.explode(mob, this.world, 3.0, 8.0f);
                        mob.discard();
                    }
                }
                case BOMBIE -> {
                    // Periodic mini-explosion every 10s if near target
                    if (active.attackTimer % 200 == 0 && target != null && mob.squaredDistanceTo(target) <= 16.0) {
                        ZombieExplosion.explode(mob, this.world, 3.5, 10.0f);
                    }
                    // Spawn Little Bombie minions every 160 ticks (8s) up to max 3 alive
                    if (active.attackTimer % 160 == 0) {
                        long littleBombies = this.activeMobs.values().stream()
                            .filter(a -> a.type == ZombieType.LITTLE_BOMBIE && a.entity != null && a.entity.isAlive())
                            .count();
                        if (littleBombies < 3) {
                            double offsetX = (this.world.random.nextDouble() - 0.5) * 2.5;
                            double offsetZ = (this.world.random.nextDouble() - 0.5) * 2.5;
                            double sx = mob.getX() + offsetX;
                            double sy = mob.getY();
                            double sz = mob.getZ() + offsetZ;
                            this.world.playSound(null, sx, sy, sz, SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.HOSTILE, 1.2f, 1.2f);
                            this.world.spawnParticles(ParticleTypes.POOF, sx, sy + 0.5, sz, 10, 0.3, 0.3, 0.3, 0.05);
                            spawnMob(ZombieType.LITTLE_BOMBIE, new MapPosition(sx, sy, sz, mob.getYaw(), 0.0f), null);
                        }
                    }
                }
                case FIRE_ZOMBIE -> {
                    if (active.attackTimer % 100 == 0 && target != null) {
                        FireballAttack.shootFireball(mob, this.world, validTargets);
                    }
                }
                case INFERNO -> {
                    FireTrailAttack.tickTrail(mob, this.world, validTargets, tickCounter);
                    if (active.attackTimer % 80 == 0 && target != null) {
                        FireballAttack.shootFireball(mob, this.world, validTargets);
                    }
                }
                case GUARDIAN_ZOMBIE -> {
                    GuardianLaserAttack.tickLaser(mob, this.world, validTargets, active.attackTimer);
                }
                case BROODMOTHER -> {
                    BroodmotherAttack.tickBroodmother(mob, this.world, validTargets, tickCounter, minion -> registerMinion(minion, ZombieType.NORMAL_EASY));
                }
                default -> {}
            }
        }
    }

    public void clearAll() {
        for (ActiveMob active : this.activeMobs.values()) {
            if (active.entity != null && active.entity.isAlive()) {
                active.entity.discard();
            }
        }
        this.activeMobs.clear();
    }

    private ServerPlayerEntity findClosestSurvivor(MobEntity mob, List<ServerPlayerEntity> survivors) {
        ServerPlayerEntity closest = null;
        double minSq = Double.MAX_VALUE;
        for (ServerPlayerEntity p : survivors) {
            double dSq = p.squaredDistanceTo(mob);
            if (dSq < minSq) {
                minSq = dSq;
                closest = p;
            }
        }
        return closest;
    }
}
