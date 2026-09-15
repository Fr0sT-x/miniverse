package dev.frost.miniverse.minigame.impl.horde.transmitter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.block.CampfireBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Random;

public class TransmitterPod {
    public enum State {
        AWAITING_ACTIVATION,
        TRANSMITTING,
        PAUSED_NO_FUEL,
        COMPLETED
    }

    private final BlockPos pos;
    private final int totalUplinkSeconds;
    private final double harvestRadius;
    private final float fuelDrainPerSecond;
    private final ServerBossBar bossBar;
    private final Random random = new Random();

    private State state = State.AWAITING_ACTIVATION;
    private float fuelPercent;
    private int secondsRemaining;
    private int tickCounter;
    private BlockPos cratePos = null;
    private BlockState previousBaseState = Blocks.AIR.getDefaultState();
    private BlockState previousAntennaState = Blocks.AIR.getDefaultState();

    public TransmitterPod(BlockPos pos, int totalUplinkSeconds, float initialFuel, double harvestRadius, float fuelDrainPerSecond) {
        this.pos = pos;
        this.totalUplinkSeconds = Math.max(30, totalUplinkSeconds);
        this.secondsRemaining = this.totalUplinkSeconds;
        this.fuelPercent = Math.max(10.0f, Math.min(100.0f, initialFuel));
        this.harvestRadius = Math.max(5.0, harvestRadius);
        this.fuelDrainPerSecond = Math.max(0.2f, fuelDrainPerSecond);

        this.bossBar = new ServerBossBar(
            Text.literal("📡 SUPPLY POD: Right-click to Boot Data Uplink!").formatted(Formatting.AQUA, Formatting.BOLD),
            BossBar.Color.BLUE,
            BossBar.Style.PROGRESS
        );
        this.bossBar.setPercent(1.0f);
        this.bossBar.setVisible(true);
    }

    public void setup(ServerWorld world) {
        if (world == null || this.pos == null) return;
        this.previousBaseState = world.getBlockState(this.pos);
        this.previousAntennaState = world.getBlockState(this.pos.up());

        // Level and ensure solid ground for the immediate 3x3 beneath the pod
        int groundY = this.pos.getY() - 1;
        BlockState groundType = world.getBlockState(new BlockPos(this.pos.getX(), groundY, this.pos.getZ()));
        if (groundType.isAir() || !groundType.getFluidState().isEmpty()) {
            groundType = Blocks.DIRT.getDefaultState();
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos gp = new BlockPos(this.pos.getX() + dx, groundY, this.pos.getZ() + dz);
                if (world.getBlockState(gp).isAir() || !world.getBlockState(gp).getFluidState().isEmpty()) {
                    world.setBlockState(gp, groundType);
                }
            }
        }

        // Clear tall grass, ferns, flowers, and leaves in 5x5 footprint for 3 blocks high
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    if (dx == 0 && dz == 0 && (dy == 0 || dy == 1)) continue; // Keep Lodestone and Campfire
                    BlockPos p = this.pos.add(dx, dy, dz);
                    BlockState s = world.getBlockState(p);
                    if (s.isOf(Blocks.SHORT_GRASS) || s.isOf(Blocks.TALL_GRASS) || s.isOf(Blocks.FERN) || s.isOf(Blocks.LARGE_FERN)
                        || s.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS) || s.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        // Place Lodestone base and Campfire transmitter
        world.setBlockState(this.pos, Blocks.LODESTONE.getDefaultState());
        world.setBlockState(this.pos.up(), Blocks.CAMPFIRE.getDefaultState());

        this.bossBar.setVisible(true);
        syncViewers(world.getPlayers());
        updateBossBar();
    }

    public boolean activate(ServerWorld world, ServerPlayerEntity activator) {
        if (this.state != State.AWAITING_ACTIVATION) return false;

        this.state = State.TRANSMITTING;
        updateBossBar();

        if (world != null) {
            world.playSound(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 2.0f, 1.2f);
            world.playSound(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.0f, 1.8f);
            world.spawnParticles(ParticleTypes.EXPLOSION, this.pos.getX() + 0.5, this.pos.getY() + 1.5, this.pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.05);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, this.pos.getX() + 0.5, this.pos.getY() + 1.5, this.pos.getZ() + 0.5, 30, 0.8, 0.8, 0.8, 0.1);
        }

        return true;
    }

    public void tick(ServerWorld world) {
        if (world == null || this.pos == null || this.state == State.COMPLETED) return;
        this.tickCounter++;

        // Ensure campfire stays lit while pod is active
        BlockPos campPos = this.pos.up();
        BlockState campState = world.getBlockState(campPos);
        if (campState.isOf(Blocks.CAMPFIRE) && campState.contains(CampfireBlock.LIT) && !campState.get(CampfireBlock.LIT)) {
            world.setBlockState(campPos, campState.with(CampfireBlock.LIT, true));
        }

        // 1. Particle Effects
        if (this.state == State.AWAITING_ACTIVATION) {
            // High vertical signal smoke pillar to lead players
            if (this.tickCounter % 4 == 0) {
                double bx = this.pos.getX() + 0.5;
                double bz = this.pos.getZ() + 0.5;
                int baseY = this.pos.getY() + 2;
                for (int y = baseY; y < baseY + 45; y += 4) {
                    world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, bx, y, bz, 1, 0.05, 0.15, 0.05, 0.01);
                }
            }
        } else {
            // Perimeter circle of glowing soul/amber particles marking the fuel harvest zone
            if (this.tickCounter % 10 == 0) {
                double cx = this.pos.getX() + 0.5;
                double cz = this.pos.getZ() + 0.5;
                int points = (int) Math.max(36, this.harvestRadius * 2.8);
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI * i) / points;
                    double px = cx + this.harvestRadius * Math.cos(angle);
                    double pz = cz + this.harvestRadius * Math.sin(angle);
                    double py = getSurfaceY(world, px, pz);
                    world.spawnParticles(this.state == State.PAUSED_NO_FUEL ? ParticleTypes.SMOKE : ParticleTypes.END_ROD, px, py, pz, 1, 0, 0, 0, 0);
                }
            }

            // Antenna sparks
            if (this.tickCounter % 6 == 0) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, this.pos.getX() + 0.5, this.pos.getY() + 1.8, this.pos.getZ() + 0.5, 2, 0.1, 0.2, 0.1, 0.02);
            }
        }

        // 2. Active Transmission Logic
        if (this.state == State.TRANSMITTING || this.state == State.PAUSED_NO_FUEL) {
            // Sonar / Radio beep pulse every 20 ticks (1s)
            if (this.tickCounter % 20 == 0) {
                if (this.state == State.TRANSMITTING) {
                    world.playSound(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 1.2f, 1.8f);
                    world.playSound(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.8f, 1.5f);
                } else {
                    // Warning horn/alarm when out of fuel!
                    world.playSound(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 1.5f, 0.5f);
                }
            }

            // Fuel drain calculation
            if (this.state == State.TRANSMITTING) {
                this.fuelPercent = Math.max(0.0f, this.fuelPercent - (this.fuelDrainPerSecond / 20.0f));

                if (this.fuelPercent <= 0.0f) {
                    this.state = State.PAUSED_NO_FUEL;
                    world.playSound(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.BLOCKS, 1.5f, 0.6f);
                } else {
                    // Timer only counts down when fuel is active!
                    if (this.tickCounter % 20 == 0) {
                        this.secondsRemaining = Math.max(0, this.secondsRemaining - 1);
                    }
                }
            }

            if (this.tickCounter % 5 == 0) {
                updateBossBar();
            }
        }
    }

    public void addFuel(float amount, Vec3d sourcePos, ServerWorld world) {
        if (this.state == State.COMPLETED || this.state == State.AWAITING_ACTIVATION) return;

        this.fuelPercent = Math.min(100.0f, this.fuelPercent + amount);

        // Resume transmission if stalled
        if (this.state == State.PAUSED_NO_FUEL && this.fuelPercent > 0.0f) {
            this.state = State.TRANSMITTING;
            if (world != null) {
                world.playSound(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1.2f, 1.4f);
            }
        }

        // Particle arc & sound
        if (world != null && sourcePos != null) {
            Vec3d target = new Vec3d(this.pos.getX() + 0.5, this.pos.getY() + 1.8, this.pos.getZ() + 0.5);
            int steps = 6;
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                double px = sourcePos.x + (target.x - sourcePos.x) * t;
                double py = sourcePos.y + (target.y - sourcePos.y) * t;
                double pz = sourcePos.z + (target.z - sourcePos.z) * t;
                world.spawnParticles(ParticleTypes.SOUL, px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
            }
            world.playSound(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.8f, 1.6f);
        }

        updateBossBar();
    }

    public void detonateEmp(ServerWorld world, int wave) {
        this.state = State.COMPLETED;

        if (world != null) {
            double cx = this.pos.getX() + 0.5;
            double cy = this.pos.getY() + 1.5;
            double cz = this.pos.getZ() + 0.5;

            // Massive EMP Shockwave
            world.playSound(null, cx, cy, cz, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.WEATHER, 3.0f, 1.2f);
            world.playSound(null, cx, cy, cz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 2.0f, 1.0f);
            world.spawnParticles(ParticleTypes.FLASH, cx, cy, cz, 3, 0.5, 0.5, 0.5, 0.0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, cx, cy, cz, 1, 0, 0, 0, 0);

            for (int i = 0; i < 36; i++) {
                double angle = (2 * Math.PI * i) / 36;
                double px = cx + 8.0 * Math.cos(angle);
                double pz = cz + 8.0 * Math.sin(angle);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, cy, pz, 3, 0.2, 0.5, 0.2, 0.1);
            }

            // Spawn Military Supply Crate right next to the Pod
            BlockPos targetCratePos = this.pos.east();
            if (!world.getBlockState(targetCratePos).isAir()) {
                targetCratePos = this.pos.north();
            }
            this.cratePos = targetCratePos;
            world.setBlockState(targetCratePos, Blocks.CHEST.getDefaultState());
            BlockEntity be = world.getBlockEntity(targetCratePos);
            if (be instanceof ChestBlockEntity chest) {
                fillMilitaryLoot(chest, wave);
            }
        }

        if (this.bossBar != null) {
            this.bossBar.setName(Text.literal("✔ DATA UPLINK COMPLETED! Crate Unlocked!").formatted(Formatting.GREEN, Formatting.BOLD));
            this.bossBar.setColor(BossBar.Color.GREEN);
            this.bossBar.setPercent(1.0f);
        }
    }

    private void fillMilitaryLoot(ChestBlockEntity chest, int wave) {
        chest.clear();
        chest.setStack(4, new ItemStack(Items.GOLDEN_APPLE, 2 + random.nextInt(2)));
        chest.setStack(11, new ItemStack(Items.ARROW, 24 + random.nextInt(16)));
        chest.setStack(13, new ItemStack(Items.COOKED_BEEF, 8));
        chest.setStack(15, new ItemStack(Items.SPLASH_POTION, 1));

        if (wave >= 5) {
            chest.setStack(22, new ItemStack(Items.DIAMOND, 2 + random.nextInt(2)));
        } else {
            chest.setStack(22, new ItemStack(Items.IRON_INGOT, 4 + random.nextInt(4)));
        }
    }

    private void updateBossBar() {
        if (this.bossBar == null) return;

        if (this.state == State.AWAITING_ACTIVATION) {
            this.bossBar.setName(Text.literal("📡 SUPPLY POD: Right-click to Boot Data Uplink!").formatted(Formatting.AQUA, Formatting.BOLD));
            this.bossBar.setColor(BossBar.Color.BLUE);
            this.bossBar.setPercent(1.0f);
        } else if (this.state == State.PAUSED_NO_FUEL) {
            this.bossBar.setName(Text.literal("⚠ UPLINK HALTED (0% FUEL) — KILL MOBS IN RING! [" + this.secondsRemaining + "s left]").formatted(Formatting.RED, Formatting.BOLD));
            this.bossBar.setColor(BossBar.Color.RED);
            this.bossBar.setPercent(0.0f);
        } else if (this.state == State.TRANSMITTING) {
            float timeRatio = (float) (this.totalUplinkSeconds - this.secondsRemaining) / (float) this.totalUplinkSeconds;
            int fuelInt = (int) this.fuelPercent;
            String fuelBar = buildFuelMiniBar(fuelInt);

            this.bossBar.setName(Text.literal("📡 UPLINK: " + (this.totalUplinkSeconds - this.secondsRemaining) + "s/" + this.totalUplinkSeconds + "s §7| §e⚡ FUEL: " + fuelBar + " §f" + fuelInt + "%")
                .formatted(fuelInt > 30 ? Formatting.GOLD : Formatting.YELLOW, Formatting.BOLD));

            this.bossBar.setPercent(Math.max(0.0f, Math.min(1.0f, timeRatio)));
            this.bossBar.setColor(fuelInt > 40 ? BossBar.Color.GREEN : (fuelInt > 20 ? BossBar.Color.YELLOW : BossBar.Color.RED));
        }
    }

    private static String buildFuelMiniBar(int percent) {
        int total = 10;
        int filled = Math.max(0, Math.min(total, (percent * total) / 100));
        return "[" + "█".repeat(filled) + "░".repeat(total - filled) + "]";
    }

    public void syncViewers(List<ServerPlayerEntity> players) {
        if (this.bossBar != null) {
            this.bossBar.setVisible(true);
            for (ServerPlayerEntity p : players) {
                if (p != null && !p.isRemoved()) {
                    if (!this.bossBar.getPlayers().contains(p)) {
                        this.bossBar.addPlayer(p);
                    }
                }
            }
        }
    }

    public void cleanup(ServerWorld world) {
        if (this.bossBar != null) {
            this.bossBar.clearPlayers();
            this.bossBar.setVisible(false);
        }
        if (this.cratePos != null && world != null) {
            world.setBlockState(this.cratePos, Blocks.AIR.getDefaultState());
            this.cratePos = null;
        }
        if (this.pos != null && world != null) {
            world.setBlockState(this.pos.up(), this.previousAntennaState != null ? this.previousAntennaState : Blocks.AIR.getDefaultState());
            world.setBlockState(this.pos, this.previousBaseState != null ? this.previousBaseState : Blocks.AIR.getDefaultState());
        }
    }

    private double getSurfaceY(ServerWorld world, double x, double z) {
        if (world == null || this.pos == null) {
            return this.pos != null ? this.pos.getY() + 0.15 : 64.15;
        }

        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);

        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return this.pos.getY() + 0.15;
        }

        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);

        // Check if topY is within reasonable relative range of the pod elevation
        if (Math.abs(topY - this.pos.getY()) <= 12) {
            BlockState below = world.getBlockState(new BlockPos(bx, topY - 1, bz));
            if (!below.isIn(BlockTags.LEAVES)) {
                // If there is water above topY, raise to surface of fluid
                BlockPos checkPos = new BlockPos(bx, topY, bz);
                while (checkPos.getY() < this.pos.getY() + 8 && !world.getFluidState(checkPos).isEmpty()) {
                    checkPos = checkPos.up();
                }
                return checkPos.getY() + 0.15;
            }
        }

        // If topY is elevated (e.g. tree leaves / overhang), scan downward around pod Y
        int searchStart = Math.min(topY, this.pos.getY() + 10);
        int searchEnd = Math.max(world.getBottomY() + 1, this.pos.getY() - 10);
        for (int y = searchStart; y >= searchEnd; y--) {
            BlockPos bp = new BlockPos(bx, y, bz);
            BlockState state = world.getBlockState(bp);
            if (!state.isAir() && !state.isIn(BlockTags.LEAVES)) {
                if (state.blocksMovement() || !state.getFluidState().isEmpty()) {
                    return y + 1.15;
                }
            }
        }

        return topY > world.getBottomY() ? topY + 0.15 : this.pos.getY() + 0.15;
    }

    public boolean isCompleted() {
        return this.state == State.TRANSMITTING && this.secondsRemaining <= 0;
    }

    public State getState() {
        return state;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockPos getCampfirePos() {
        return pos != null ? pos.up() : null;
    }

    public BlockPos getCratePos() {
        return cratePos;
    }

    public double getHarvestRadius() {
        return harvestRadius;
    }

    public float getFuelPercent() {
        return fuelPercent;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public ServerBossBar getBossBar() {
        return bossBar;
    }
}
