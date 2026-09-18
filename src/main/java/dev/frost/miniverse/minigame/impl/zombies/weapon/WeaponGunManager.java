package dev.frost.miniverse.minigame.impl.zombies.weapon;

import dev.frost.miniverse.minigame.impl.zombies.mob.ZombieEntityManager;
import dev.frost.miniverse.minigame.impl.zombies.perk.PlayerPerk;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public class WeaponGunManager {
    public static class ReloadTask {
        public final UUID playerUuid;
        public final ItemStack stack;
        public final WeaponType type;
        public final int totalTicks;
        public int ticksRemaining;

        public ReloadTask(UUID playerUuid, ItemStack stack, WeaponType type, int ticks) {
            this.playerUuid = playerUuid;
            this.stack = stack;
            this.type = type;
            this.totalTicks = ticks;
            this.ticksRemaining = ticks;
        }
    }

    private final ServerWorld world;
    private final ZombieEntityManager mobManager;
    private final BiConsumer<ServerPlayerEntity, Integer> goldAwarder;
    private final Supplier<Boolean> instantKillSupplier;
    private final Supplier<Boolean> doubleGoldSupplier;
    private final BiPredicate<ServerPlayerEntity, PlayerPerk> perkChecker;
    private java.util.function.Consumer<ServerPlayerEntity> killTracker;

    private final Map<UUID, Long> lastShotTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastOutOfAmmoChatTimes = new ConcurrentHashMap<>();
    private final Map<UUID, ReloadTask> activeReloads = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public WeaponGunManager(
        ServerWorld world,
        ZombieEntityManager mobManager,
        BiConsumer<ServerPlayerEntity, Integer> goldAwarder,
        Supplier<Boolean> instantKillSupplier,
        Supplier<Boolean> doubleGoldSupplier,
        BiPredicate<ServerPlayerEntity, PlayerPerk> perkChecker
    ) {
        this.world = world;
        this.mobManager = mobManager;
        this.goldAwarder = goldAwarder;
        this.instantKillSupplier = instantKillSupplier;
        this.doubleGoldSupplier = doubleGoldSupplier;
        this.perkChecker = perkChecker;
    }

    public void setKillTracker(java.util.function.Consumer<ServerPlayerEntity> killTracker) {
        this.killTracker = killTracker;
    }

    public boolean isReloading(UUID playerUuid) {
        return this.activeReloads.containsKey(playerUuid);
    }

    public void tickReloads() {
        if (this.activeReloads.isEmpty()) return;

        Iterator<Map.Entry<UUID, ReloadTask>> it = this.activeReloads.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ReloadTask> entry = it.next();
            ReloadTask task = entry.getValue();
            ServerPlayerEntity player = this.world.getServer().getPlayerManager().getPlayer(task.playerUuid);

            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }

            task.ticksRemaining--;

            // Animate item durability bar during reload
            int maxDmg = task.stack.getMaxDamage();
            if (maxDmg > 0 && task.totalTicks > 0) {
                float frac = (float) task.ticksRemaining / task.totalTicks;
                task.stack.setDamage(Math.max(1, (int) (maxDmg * frac)));
            }

            if (task.ticksRemaining <= 0) {
                completeReload(player, task);
                it.remove();
            }
        }
    }

    public void tickHUD(List<ServerPlayerEntity> survivors) {
        for (ServerPlayerEntity player : survivors) {
            if (player == null || !player.isAlive() || player.isSpectator()) continue;

            // Ensure all guns in player's inventory display their clip ammo as their stack count
            boolean invCountChanged = false;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack invStack = player.getInventory().getStack(i);
                WeaponType wt = WeaponItemHelper.getWeaponType(invStack);
                if (wt != null && !wt.getData().isMelee()) {
                    int clip = WeaponItemHelper.getClipAmmo(invStack);
                    int expectedCount = Math.max(1, clip);
                    if (invStack.getCount() != expectedCount) {
                        invStack.setCount(expectedCount);
                        invCountChanged = true;
                    }
                }
            }
            if (invCountChanged) {
                player.playerScreenHandler.sendContentUpdates();
            }

            ItemStack held = player.getMainHandStack();
            WeaponType type = WeaponItemHelper.getWeaponType(held);

            if (type != null) {
                if (type.getData().isMelee()) {
                    if (player.experienceLevel != 0 || player.experienceProgress != 1.0f) {
                        player.setExperienceLevel(0);
                        player.experienceProgress = 1.0f;
                        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket(1.0f, player.totalExperience, 0));
                    }
                } else {
                    int clip = WeaponItemHelper.getClipAmmo(held);
                    int reserve = WeaponItemHelper.getReserveAmmo(held);
                    int totalAmmo = clip + reserve;

                    float expBarProgress;
                    if (isReloading(player.getUuid())) {
                        ReloadTask task = this.activeReloads.get(player.getUuid());
                        expBarProgress = task != null && task.totalTicks > 0
                            ? (float) (task.totalTicks - task.ticksRemaining) / task.totalTicks
                            : 0.0f;
                    } else {
                        Long lastShot = this.lastShotTimes.get(player.getUuid());
                        int delayTicks = type.getData().delayTicks();
                        if (this.perkChecker.test(player, PlayerPerk.QUICK_FIRE)) {
                            delayTicks = Math.max(1, (int) (delayTicks * 0.75));
                        }
                        long delayMillis = delayTicks * 50L;
                        if (lastShot == null || (System.currentTimeMillis() - lastShot) >= delayMillis) {
                            expBarProgress = 1.0f;
                        } else {
                            expBarProgress = (float) (System.currentTimeMillis() - lastShot) / delayMillis;
                        }
                    }

                    expBarProgress = Math.clamp(expBarProgress, 0.0f, 1.0f);
                    player.setExperienceLevel(totalAmmo);
                    player.experienceProgress = expBarProgress;
                    player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket(expBarProgress, player.totalExperience, totalAmmo));

                    if (totalAmmo == 0) {
                        player.sendMessage(Text.literal("OUT OF AMMO").formatted(Formatting.RED, Formatting.BOLD), true);
                    }
                }
            } else {
                if (player.experienceLevel != 0 || player.experienceProgress != 0.0f) {
                    player.setExperienceLevel(0);
                    player.experienceProgress = 0.0f;
                    player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket(0.0f, player.totalExperience, 0));
                }
            }
        }
    }

    public void triggerReload(ServerPlayerEntity player, ItemStack stack, WeaponType type) {
        if (type == null || type.getData().isMelee()) return;
        if (this.activeReloads.containsKey(player.getUuid())) return;

        int currentClip = WeaponItemHelper.getClipAmmo(stack);
        int reserve = WeaponItemHelper.getReserveAmmo(stack);

        if (currentClip >= type.getData().clipSize() || reserve <= 0) {
            return;
        }

        int reloadTicks = type.getData().reloadTicks();
        if (this.perkChecker.test(player, PlayerPerk.QUICK_FIRE)) {
            reloadTicks = (int) (reloadTicks * 0.70); // 30% faster reload
        }

        this.activeReloads.put(player.getUuid(), new ReloadTask(player.getUuid(), stack, type, reloadTicks));
        WeaponItemHelper.updateStackLore(stack, type, currentClip, reserve, true);

        player.sendMessage(Text.literal("Reloading...").formatted(Formatting.RED), true);
        this.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    private void completeReload(ServerPlayerEntity player, ReloadTask task) {
        int currentClip = WeaponItemHelper.getClipAmmo(task.stack);
        int reserve = WeaponItemHelper.getReserveAmmo(task.stack);
        int needed = task.type.getData().clipSize() - currentClip;

        int toAdd = Math.min(needed, reserve);
        int newClip = currentClip + toAdd;
        int newReserve = reserve - toAdd;

        task.stack.setDamage(0);
        task.stack.setCount(Math.max(1, newClip));
        WeaponItemHelper.setAmmo(task.stack, task.type, newClip, newReserve, false);
        this.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.PLAYERS, 0.8f, 1.4f);
        player.sendMessage(Text.empty(), true);
    }

    public boolean handleWeaponFire(ServerPlayerEntity player, ItemStack stack) {
        WeaponType type = WeaponItemHelper.getWeaponType(stack);
        if (type == null) return false;

        WeaponData data = type.getData();
        long now = System.currentTimeMillis();

        int delayTicks = data.delayTicks();
        if (this.perkChecker.test(player, PlayerPerk.QUICK_FIRE)) {
            delayTicks = Math.max(1, (int) (delayTicks * 0.75));
        }
        long delayMillis = delayTicks * 50L;

        Long lastShot = this.lastShotTimes.get(player.getUuid());
        if (lastShot != null && (now - lastShot) < delayMillis) {
            return false;
        }

        if (isReloading(player.getUuid())) {
            player.sendMessage(Text.literal("Reloading...").formatted(Formatting.RED), true);
            return false;
        }

        // Melee Knife
        if (data.isMelee()) {
            this.lastShotTimes.put(player.getUuid(), now);
            fireKnife(player, data);
            return true;
        }

        int clip = WeaponItemHelper.getClipAmmo(stack);
        int reserve = WeaponItemHelper.getReserveAmmo(stack);

        if (clip <= 0) {
            if (reserve > 0) {
                triggerReload(player, stack, type);
            } else {
                this.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.6f, 1.5f);
                Long lastChat = this.lastOutOfAmmoChatTimes.get(player.getUuid());
                if (lastChat == null || (now - lastChat) >= 1500L) {
                    this.lastOutOfAmmoChatTimes.put(player.getUuid(), now);
                    player.sendMessage(Text.literal("This weapon is out of ammo! You can refill ammo at the place that you purchased the weapon or through collecting the Max Ammo Power Up.").formatted(Formatting.RED), false);
                }
            }
            return false;
        }

        // Spend bullet
        clip--;
        WeaponItemHelper.setAmmo(stack, type, clip, reserve, false);
        stack.setCount(Math.max(1, clip));
        this.lastShotTimes.put(player.getUuid(), now);

        player.experienceProgress = 0.0f;
        player.setExperienceLevel(clip + reserve);
        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket(0.0f, player.totalExperience, clip + reserve));

        if (clip == 0 && reserve > 0) {
            triggerReload(player, stack, type);
        }

        // Fire bullets
        fireGun(player, type, data);
        return true;
    }

    private void fireKnife(ServerPlayerEntity player, WeaponData data) {
        Vec3d eyePos = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d reach = eyePos.add(look.multiply(3.0));

        this.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.0f);

        Box targetBox = player.getBoundingBox().stretch(look.multiply(3.0)).expand(1.0);
        for (Entity e : this.world.getOtherEntities(player, targetBox)) {
            if (e instanceof MobEntity mob && this.mobManager.isZombieMob(mob) && mob.isAlive()) {
                var active = this.mobManager.getActiveMob(mob);
                boolean isBoss = active != null && active.type != null && active.type.isBoss();
                float dmg = (this.instantKillSupplier.get() && !isBoss) ? 9999.0f : data.damage();
                boolean wasAlive = mob.isAlive();
                mob.damage(this.world.getDamageSources().playerAttack(player), dmg);

                // Knife knockback: push mob backwards
                Vec3d kb = look.normalize().multiply(1.2).add(0, 0.25, 0);
                mob.setVelocity(kb);
                mob.velocityModified = true;

                if (wasAlive && (!mob.isAlive() || mob.getHealth() <= 0.0f) && this.killTracker != null) {
                    this.killTracker.accept(player);
                }
                int gold = data.goldPerHit();
                if (this.doubleGoldSupplier.get()) gold *= 2;
                this.goldAwarder.accept(player, gold);
                break;
            }
        }
    }

    private void fireGun(ServerPlayerEntity player, WeaponType type, WeaponData data) {
        Vec3d origin = player.getEyePos();
        Vec3d lookDir = player.getRotationVec(1.0f);

        playFireSound(player, type);

        // Rocket Launcher & Nuke Launcher
        if (type == WeaponType.ROCKET_LAUNCHER || type == WeaponType.NUKE_LAUNCHER) {
            Vec3d target = origin.add(lookDir.multiply(40.0));
            BlockHitResult blockHit = this.world.raycast(new RaycastContext(origin, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            Vec3d hitPos = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getPos() : target;

            // Check if rocket hits a mob directly before reaching the block
            Box sweepBox = new Box(origin, hitPos).expand(0.5);
            double closestDistSq = origin.squaredDistanceTo(hitPos);
            for (Entity entity : this.world.getOtherEntities(player, sweepBox)) {
                if (entity instanceof MobEntity mob && this.mobManager.isZombieMob(mob) && mob.isAlive()) {
                    Box mobBox = mob.getBoundingBox().expand(0.2);
                    Optional<Vec3d> hitVec = mobBox.raycast(origin, hitPos);
                    if (hitVec.isPresent()) {
                        double dSq = origin.squaredDistanceTo(hitVec.get());
                        if (dSq < closestDistSq) {
                            closestDistSq = dSq;
                            hitPos = hitVec.get();
                        }
                    }
                }
            }

            spawnBulletTrail(origin, hitPos, type == WeaponType.NUKE_LAUNCHER ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.CAMPFIRE_COSY_SMOKE);
            double radius = type == WeaponType.NUKE_LAUNCHER ? 6.5 : 4.0;
            this.world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, hitPos.x, hitPos.y, hitPos.z, 1, 0, 0, 0, 0);
            this.world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0f, 1.0f);

            final Vec3d finalHitPos = hitPos;
            double radiusSq = radius * radius;
            List<MobEntity> nearbyMobs = this.world.getEntitiesByClass(
                MobEntity.class,
                new Box(hitPos.x - radius, hitPos.y - radius, hitPos.z - radius, hitPos.x + radius, hitPos.y + radius, hitPos.z + radius),
                m -> this.mobManager.isZombieMob(m) && m.isAlive() && m.squaredDistanceTo(finalHitPos) <= radiusSq
            );

            for (MobEntity mob : nearbyMobs) {
                var active = this.mobManager.getActiveMob(mob);
                boolean isBoss = active != null && active.type != null && active.type.isBoss();
                float damage = (this.instantKillSupplier != null && this.instantKillSupplier.get() && !isBoss) ? 9999.0f : data.damage();

                boolean wasAlive = mob.isAlive();
                mob.damage(this.world.getDamageSources().playerAttack(player), damage);
                if (wasAlive && (!mob.isAlive() || mob.getHealth() <= 0.0f) && this.killTracker != null) {
                    this.killTracker.accept(player);
                }

                int gold = data.goldPerHit();
                if (this.doubleGoldSupplier != null && this.doubleGoldSupplier.get()) {
                    gold *= 2;
                }
                if (this.goldAwarder != null && gold > 0) {
                    this.goldAwarder.accept(player, gold);
                }
            }
            return;
        }

        int bullets = data.bulletsPerShot();
        double spread = data.spread();

        for (int b = 0; b < bullets; b++) {
            Vec3d dir = lookDir;
            if (spread > 0.0 && (bullets > 1 || spread > 0.05)) {
                double ox = (this.random.nextGaussian() * spread * 0.04);
                double oy = (this.random.nextGaussian() * spread * 0.04);
                double oz = (this.random.nextGaussian() * spread * 0.04);
                dir = lookDir.add(ox, oy, oz).normalize();
            }

            double maxRange = type == WeaponType.FLAME_THROWER ? 10.0 : 45.0;
            Vec3d end = origin.add(dir.multiply(maxRange));

            // Block Raycast
            BlockHitResult blockHit = this.world.raycast(new RaycastContext(origin, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            Vec3d actualEnd = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getPos() : end;

            // Mob Raycast
            Box sweepBox = new Box(origin, actualEnd).expand(1.0);
            List<EntityHit> hitMobs = new ArrayList<>();

            for (Entity entity : this.world.getOtherEntities(player, sweepBox)) {
                if (entity instanceof MobEntity mob && this.mobManager.isZombieMob(mob) && mob.isAlive()) {
                    Box mobBox = mob.getBoundingBox().expand(0.2);
                    Optional<Vec3d> hitVec = mobBox.raycast(origin, actualEnd);
                    hitVec.ifPresent(vec3d -> hitMobs.add(new EntityHit(mob, vec3d, origin.squaredDistanceTo(vec3d))));
                }
            }

            hitMobs.sort(Comparator.comparingDouble(EntityHit::distSq));

            int maxHits = data.isPiercing() ? data.pierceLimit() : 1;
            int hits = 0;
            Vec3d finalTraceEnd = actualEnd;

            for (EntityHit hit : hitMobs) {
                if (hits >= maxHits) break;
                hits++;
                finalTraceEnd = hit.pos();

                MobEntity mob = hit.mob();
                Box mobBox = mob.getBoundingBox();
                double mobHeight = mobBox.maxY - mobBox.minY;
                boolean headshot = (hit.pos().y - mobBox.minY) >= (mobHeight * 0.72);

                float finalDamage = data.damage();
                if (headshot) {
                    finalDamage *= 2.0f;
                    this.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.8f, 1.8f);
                    this.world.spawnParticles(ParticleTypes.CRIT, hit.pos().x, hit.pos().y, hit.pos().z, 6, 0.1, 0.1, 0.1, 0.05);
                }

                // Perk modifiers
                if (this.perkChecker.test(player, PlayerPerk.FROZEN_BULLETS)) {
                    mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1, false, false, true));
                    this.world.spawnParticles(ParticleTypes.SNOWFLAKE, hit.pos().x, hit.pos().y, hit.pos().z, 5, 0.2, 0.2, 0.2, 0.02);
                }
                if (this.perkChecker.test(player, PlayerPerk.FLAME_BULLETS)) {
                    mob.setOnFireFor(3);
                    this.world.spawnParticles(ParticleTypes.FLAME, hit.pos().x, hit.pos().y, hit.pos().z, 4, 0.2, 0.2, 0.2, 0.02);
                }
                if (this.instantKillSupplier.get()) {
                    var active = this.mobManager.getActiveMob(mob);
                    if (active == null || active.type == null || !active.type.isBoss()) {
                        finalDamage = 9999.0f;
                    }
                }

                boolean wasAlive = mob.isAlive();
                mob.damage(this.world.getDamageSources().playerAttack(player), finalDamage);
                if (wasAlive && (!mob.isAlive() || mob.getHealth() <= 0.0f) && this.killTracker != null) {
                    this.killTracker.accept(player);
                }

                int gold = data.goldPerHit();
                if (headshot) gold += 10;
                if (this.doubleGoldSupplier.get()) gold *= 2;
                this.goldAwarder.accept(player, gold);
            }

            spawnBulletTrail(origin, finalTraceEnd, data.bulletParticle());
        }
    }

    private void spawnBulletTrail(Vec3d start, Vec3d end, ParticleEffect particle) {
        Vec3d delta = end.subtract(start);
        double dist = delta.length();
        Vec3d step = delta.normalize().multiply(0.8);
        int steps = (int) (dist / 0.8);

        Vec3d cur = start;
        for (int i = 0; i < steps; i++) {
            cur = cur.add(step);
            this.world.spawnParticles(particle, cur.x, cur.y, cur.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void playFireSound(ServerPlayerEntity player, WeaponType type) {
        SoundEvent sound = switch (type) {
            case PISTOL -> SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST;
            case SHOTGUN -> SoundEvents.ENTITY_GENERIC_EXPLODE.value();
            case RIFLE -> SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST;
            case SNIPER -> SoundEvents.ENTITY_GENERIC_EXPLODE.value();
            case FLAME_THROWER -> SoundEvents.ITEM_FIRECHARGE_USE;
            case GOLD_DIGGER -> SoundEvents.ENTITY_ARROW_HIT_PLAYER;
            case ZOMBIE_ZAPPER -> SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER;
            default -> SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST;
        };

        float pitch = switch (type) {
            case PISTOL -> 1.7f;
            case SHOTGUN -> 1.3f;
            case RIFLE -> 1.9f;
            case SNIPER -> 2.0f;
            case FLAME_THROWER -> 1.1f;
            case GOLD_DIGGER -> 1.6f;
            case ZOMBIE_ZAPPER -> 1.8f;
            default -> 1.5f;
        };

        this.world.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, 0.9f, pitch);
    }

    private record EntityHit(MobEntity mob, Vec3d pos, double distSq) {}
}
