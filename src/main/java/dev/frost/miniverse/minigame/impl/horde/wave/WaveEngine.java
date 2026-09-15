package dev.frost.miniverse.minigame.impl.horde.wave;

import dev.frost.miniverse.minigame.impl.horde.HordeSurvivalMinigame;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WaveEngine {
    public static final String TAG_HORDE = "horde_mob";
    public static final double MIN_SPAWN_RADIUS = 28.0;
    public static final double MAX_SPAWN_RADIUS = 40.0;

    private final HordeSurvivalMinigame minigame;
    private final Random random = new Random();
    private final Set<UUID> activeWaveMobs = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SpawnType> spawnedMobTypes = new ConcurrentHashMap<>();

    private int currentWave;
    private WaveDefinition currentDef;
    private int tickCounter;
    private boolean needsInitialBurst = false;

    public enum SpawnType {
        ZOMBIE, SKELETON, SPIDER, CREEPER, MINER_ZOMBIE, HARPOON_DROWNED, SAPPER_CREEPER
    }

    public WaveEngine(HordeSurvivalMinigame minigame) {
        this.minigame = minigame;
    }

    public void startWave(int wave, int playerCount) {
        this.currentWave = wave;
        this.currentDef = WaveDefinition.computeFor(wave, playerCount);
        this.needsInitialBurst = true;
    }

    public void tick(ServerWorld world, List<ServerPlayerEntity> players) {
        if (this.currentDef == null) return;
        this.tickCounter++;

        BlockPos center = getHordeAnchorPos(players);
        if (center == null) return;

        int totalJoinedPlayers = Math.max(1, this.minigame.getParticipants().size());

        // 1. Initial spawn burst when wave starts, scaled to joined player count
        if (this.needsInitialBurst) {
            this.needsInitialBurst = false;
            int burstCount = Math.min(2 + totalJoinedPlayers * 2, this.currentDef.concurrentMobCap());
            for (int i = 0; i < burstCount; i++) {
                SpawnType type = pickSpawnTypeWithLimits(this.random, totalJoinedPlayers);
                spawnMobAround(world, center, type, players);
            }
        }

        // 2. Continuously replenish horde mobs around campfire up to concurrent cap
        if (this.tickCounter % 20 == 0) { // Every 1 second
            if (this.activeWaveMobs.size() < this.currentDef.concurrentMobCap()) {
                int toSpawn = Math.min(2, this.currentDef.concurrentMobCap() - this.activeWaveMobs.size());
                for (int i = 0; i < toSpawn; i++) {
                    SpawnType type = pickSpawnTypeWithLimits(this.random, totalJoinedPlayers);
                    spawnMobAround(world, center, type, players);
                }
            }
        }

        // 3. Relentless Convergence AI & Siege abilities every 10 ticks (0.5s)
        if (this.tickCounter % 10 == 0) {
            for (UUID mobId : this.activeWaveMobs) {
                Entity entity = world.getEntity(mobId);
                if (entity instanceof MobEntity mob && mob.isAlive()) {
                    // Cull hopelessly strayed mobs (> 85m away from campfire, e.g. stuck deep in caves)
                    if (mob.squaredDistanceTo(center.toCenterPos()) > 85.0 * 85.0) {
                        this.activeWaveMobs.remove(mobId);
                        this.spawnedMobTypes.remove(mobId);
                        mob.discard();
                        continue;
                    }

                    // Run siege mob custom ability logic
                    SiegeMobManager.tickMob(mob, world, players);

                    // Relentless Convergence: Ensure mob is aggressively targeting players or rushing the campfire
                    LivingEntity currentTarget = mob.getTarget();
                    boolean hasValidTarget = currentTarget instanceof ServerPlayerEntity sp && players.contains(sp) && sp.isAlive();

                    if (!hasValidTarget) {
                        ServerPlayerEntity closest = findClosestSurvivor(mob.getBlockPos(), players);
                        if (closest != null && mob.squaredDistanceTo(closest) <= 64.0 * 64.0) {
                            mob.setTarget(closest);
                            mob.getNavigation().startMovingTo(closest.getX(), closest.getY(), closest.getZ(), 1.05);
                        } else {
                            // March directly into the campfire
                            mob.getNavigation().startMovingTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.05);
                        }
                    } else if (!mob.getNavigation().isFollowingPath()) {
                        // Keep path active towards target or campfire
                        mob.getNavigation().startMovingTo(currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(), 1.05);
                    }
                } else if (entity == null || !entity.isAlive()) {
                    this.activeWaveMobs.remove(mobId);
                    this.spawnedMobTypes.remove(mobId);
                }
            }
        }
    }

    public SpawnType pickSpawnTypeWithLimits(Random random, int totalJoinedPlayers) {
        int maxSkeletons = totalJoinedPlayers;
        int maxSpiders = totalJoinedPlayers;

        int currentSkeletons = countActiveType(SpawnType.SKELETON);
        int currentSpiders = countActiveType(SpawnType.SPIDER);

        SpawnType picked = this.currentDef.pickSpawnType(random);

        // Limit skeletons: max 1 per player joined
        if (picked == SpawnType.SKELETON && currentSkeletons >= maxSkeletons) {
            return SpawnType.ZOMBIE;
        }

        // Limit spiders: max 1 per player joined
        if (picked == SpawnType.SPIDER && currentSpiders >= maxSpiders) {
            return SpawnType.ZOMBIE;
        }

        return picked;
    }

    public int countActiveType(SpawnType type) {
        int count = 0;
        for (SpawnType t : this.spawnedMobTypes.values()) {
            if (t == type) count++;
        }
        return count;
    }

    private void spawnMobAround(ServerWorld world, BlockPos center, SpawnType type, List<ServerPlayerEntity> players) {
        BlockPos spawnPos = findSpawnPositionAround(world, center);
        if (spawnPos == null) return;

        MobEntity mob = createMob(world, type);
        if (mob == null) return;

        mob.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, random.nextFloat() * 360.0f, 0);
        mob.initialize(world, world.getLocalDifficulty(spawnPos), SpawnReason.EVENT, null);
        mob.addCommandTag(TAG_HORDE);
        mob.setPersistent();

        // Boost follow range so mob detects survivors from across the perimeter
        var followAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followAttr != null) {
            followAttr.setBaseValue(64.0);
        }

        // Sunlight protection for undead
        if (mob instanceof ZombieEntity || mob instanceof SkeletonEntity) {
            if (mob.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
                mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            }
        }

        // Milestone wave special buffs
        if (this.currentDef != null && this.currentDef.milestone() == WaveDefinition.MilestoneType.BLOOD_MOON) {
            if (mob instanceof CreeperEntity c) {
                net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
                nbt.putBoolean("powered", true);
                c.readCustomDataFromNbt(nbt);
            }
        } else if (this.currentDef != null && this.currentDef.milestone() == WaveDefinition.MilestoneType.SHADOW_PLAGUE) {
            if (mob instanceof SpiderEntity s) {
                s.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 99999, 1, false, false, true));
            }
        }

        world.spawnEntity(mob);

        // Immediate pathfinding & target lock
        ServerPlayerEntity closest = findClosestSurvivor(spawnPos, players);
        if (closest != null) {
            mob.setTarget(closest);
            mob.getNavigation().startMovingTo(closest.getX(), closest.getY(), closest.getZ(), 1.05);
        } else {
            mob.getNavigation().startMovingTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.05);
        }

        this.activeWaveMobs.add(mob.getUuid());
        this.spawnedMobTypes.put(mob.getUuid(), type);
    }

    private BlockPos findSpawnPositionAround(ServerWorld world, BlockPos center) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = MIN_SPAWN_RADIUS + random.nextDouble() * (MAX_SPAWN_RADIUS - MIN_SPAWN_RADIUS);
            int x = (int) (center.getX() + dist * Math.cos(angle));
            int z = (int) (center.getZ() + dist * Math.sin(angle));

            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= world.getBottomY() + 3) continue;

            BlockPos feetPos = new BlockPos(x, y, z);
            BlockPos groundPos = feetPos.down();

            BlockState ground = world.getBlockState(groundPos);
            BlockState feet = world.getBlockState(feetPos);
            BlockState head = world.getBlockState(feetPos.up());

            // Ensure dry, solid ground
            if (ground.isAir() || !ground.getFluidState().isEmpty() || !ground.isSolidBlock(world, groundPos)) {
                continue;
            }

            // Ensure clear space for mob body
            if (!feet.isAir() && !feet.isReplaceable()) continue;
            if (!head.isAir() && !head.isReplaceable()) continue;
            if (!feet.getFluidState().isEmpty()) continue;

            return feetPos;
        }

        // Fallback: top motion blocking near radius
        int fx = (int) (center.getX() + MIN_SPAWN_RADIUS);
        int fz = (int) (center.getZ() + MIN_SPAWN_RADIUS);
        int fy = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, fx, fz);
        return new BlockPos(fx, fy, fz);
    }

    private ServerPlayerEntity findClosestSurvivor(BlockPos pos, List<ServerPlayerEntity> players) {
        if (players == null) return null;
        ServerPlayerEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (ServerPlayerEntity p : players) {
            if (p != null && p.isAlive()) {
                double distSq = pos.getSquaredDistance(p.getBlockPos());
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = p;
                }
            }
        }
        return best;
    }

    private BlockPos getHordeAnchorPos(List<ServerPlayerEntity> players) {
        BlockPos campPos = this.minigame.getCurrentCampfirePos();
        if (campPos != null) {
            return campPos;
        }
        if (players != null && !players.isEmpty()) {
            return players.get(0).getBlockPos();
        }
        return null;
    }

    private MobEntity createMob(ServerWorld world, SpawnType type) {
        return switch (type) {
            case ZOMBIE -> new ZombieEntity(EntityType.ZOMBIE, world);
            case SKELETON -> new SkeletonEntity(EntityType.SKELETON, world);
            case SPIDER -> new SpiderEntity(EntityType.SPIDER, world);
            case CREEPER -> new CreeperEntity(EntityType.CREEPER, world);
            case MINER_ZOMBIE -> {
                ZombieEntity z = new ZombieEntity(EntityType.ZOMBIE, world);
                SiegeMobManager.applyMinerZombie(z);
                yield z;
            }
            case HARPOON_DROWNED -> {
                DrownedEntity d = new DrownedEntity(EntityType.DROWNED, world);
                SiegeMobManager.applyHarpoonDrowned(d);
                yield d;
            }
            case SAPPER_CREEPER -> {
                CreeperEntity c = new CreeperEntity(EntityType.CREEPER, world);
                SiegeMobManager.applySapperCreeper(c);
                yield c;
            }
        };
    }

    public boolean handleMobDeath(UUID entityUuid, ServerPlayerEntity killer) {
        if (this.activeWaveMobs.remove(entityUuid)) {
            SpawnType type = this.spawnedMobTypes.remove(entityUuid);
            if (killer != null) {
                boolean isSiege = type == SpawnType.MINER_ZOMBIE || type == SpawnType.HARPOON_DROWNED || type == SpawnType.SAPPER_CREEPER;
                int reward = isSiege ? 15 : 5;

                // Milestone waves double coins!
                if (this.currentDef != null && (this.currentDef.milestone() == WaveDefinition.MilestoneType.BLOOD_MOON || this.currentDef.milestone() == WaveDefinition.MilestoneType.SHADOW_PLAGUE)) {
                    reward *= 2;
                }

                this.minigame.addCoins(killer.getUuid(), reward);
                if (type != null && this.minigame.getBountyManager() != null) {
                    this.minigame.getBountyManager().onMobKilled(killer.getUuid(), type);
                }
            }
            return true;
        }
        return false;
    }

    public void purgeWaveMobs(ServerWorld world) {
        for (UUID id : this.activeWaveMobs) {
            Entity e = world.getEntity(id);
            if (e != null) {
                world.spawnParticles(ParticleTypes.FLAME, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
                world.spawnParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
                e.discard();
            }
        }
        this.activeWaveMobs.clear();
        this.spawnedMobTypes.clear();
    }

    public int getActiveMobCount() {
        return this.activeWaveMobs.size();
    }

    public int getConcurrentMobCap() {
        return this.currentDef != null ? this.currentDef.concurrentMobCap() : 0;
    }

    public WaveDefinition getCurrentDef() {
        return currentDef;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public void cleanup(ServerWorld world) {
        purgeWaveMobs(world);
    }
}
