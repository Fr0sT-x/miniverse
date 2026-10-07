package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMapConfig;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.*;

public class TargetShootRule implements MicroRule {
    private final Random random = new Random();
    private final List<BlockPos> activeTargetPositions = new ArrayList<>();
    private final Set<UUID> passedPlayers = new HashSet<>();

    @Override
    public String id() {
        return "target_shoot";
    }

    @Override
    public String name() {
        return "Target Shoot";
    }

    @Override
    public String description() {
        return "Fire your loaded crossbow to hit a floating target.";
    }

    @Override
    public Text title() {
        return Text.literal("SHOOT A TARGET!").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Hit any floating target with your crossbow!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public double minDurationSeconds() {
        return 4.0;
    }

    @Override
    public boolean isApplicable(MicroPartyMapConfig mapConfig) {
        return true;
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.activeTargetPositions.clear();
        this.passedPlayers.clear();

        ServerWorld world = game.getWorld();
        if (world == null) return;

        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());

        // 1. Position target blocks
        List<MapPosition> staticTargets = game.getMapConfig().targets();
        if (!staticTargets.isEmpty()) {
            for (MapPosition p : staticTargets) {
                BlockPos targetPos = new BlockPos((int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()));
                game.getBlockManager().setTemporaryBlock(world, targetPos, Blocks.TARGET.getDefaultState());
                this.activeTargetPositions.add(targetPos);
            }
        } else {
            // Procedurally spawn 6 to 10 targets in the air around the arena
            int count = 6 + random.nextInt(5);
            int margin = Math.max(1, bounds.width() / 8);

            for (int i = 0; i < count; i++) {
                int tx = bounds.minX() + margin + random.nextInt(Math.max(1, bounds.width() - 2 * margin));
                int tz = bounds.minZ() + margin + random.nextInt(Math.max(1, bounds.depth() - 2 * margin));
                int ty = floorY + 1 + random.nextInt(3); // 1 to 3 blocks above floor

                BlockPos targetPos = new BlockPos(tx, ty, tz);
                game.getBlockManager().setTemporaryBlock(world, targetPos, Blocks.TARGET.getDefaultState());
                this.activeTargetPositions.add(targetPos);
            }
        }

        // 2. Equip living players with a pre-loaded Crossbow and backup arrows
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            ItemStack crossbow = new ItemStack(Items.CROSSBOW);
            crossbow.set(DataComponentTypes.CUSTOM_NAME, Text.literal("⚡ Micro-Crossbow").formatted(Formatting.GOLD, Formatting.BOLD));
            ChargedProjectilesComponent charged = ChargedProjectilesComponent.of(List.of(new ItemStack(Items.ARROW)));
            crossbow.set(DataComponentTypes.CHARGED_PROJECTILES, charged);

            p.getInventory().setStack(0, crossbow);
            p.getInventory().setStack(9, new ItemStack(Items.ARROW, 3));
            p.playSoundToPlayer(SoundEvents.ITEM_CROSSBOW_LOADING_END.value(), SoundCategory.PLAYERS, 0.9f, 1.2f);
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null || this.activeTargetPositions.isEmpty()) return;

        Iterator<BlockPos> it = this.activeTargetPositions.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            BlockState state = world.getBlockState(pos);

            boolean isPowered = state.isOf(Blocks.TARGET) && state.contains(net.minecraft.state.property.Properties.POWER) && state.get(net.minecraft.state.property.Properties.POWER) > 0;
            Box checkArea = new Box(pos).expand(1.8);
            List<PersistentProjectileEntity> projectiles = world.getEntitiesByClass(PersistentProjectileEntity.class, checkArea, Entity::isAlive);

            if (isPowered || !projectiles.isEmpty()) {
                // Find owner of projectile or closest unpassed living player
                ServerPlayerEntity shooter = null;
                for (PersistentProjectileEntity proj : projectiles) {
                    if (proj.getOwner() instanceof ServerPlayerEntity owner && game.getLivingPlayers().contains(owner) && !passedPlayers.contains(owner.getUuid())) {
                        shooter = owner;
                        proj.discard();
                        break;
                    }
                }

                if (shooter == null) {
                    // Fallback to closest unpassed living player
                    double bestDist = 100.0;
                    for (ServerPlayerEntity p : game.getLivingPlayers()) {
                        if (!passedPlayers.contains(p.getUuid())) {
                            double dist = p.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                            if (dist < bestDist) {
                                bestDist = dist;
                                shooter = p;
                            }
                        }
                    }
                }

                if (shooter != null) {
                    this.passedPlayers.add(shooter.getUuid());
                    shooter.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.6f);
                    shooter.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 2.0f);
                    shooter.networkHandler.sendPacket(new OverlayMessageS2CPacket(Text.literal("§a§l🎯 TARGET HIT!").formatted(Formatting.GREEN)));

                    // Visual sparkle and reward effect at target
                    world.spawnParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16, 0.4, 0.4, 0.4, 0.2);
                    world.spawnParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.1);

                    // Turn hit target into gold/emerald block to indicate it was hit
                    game.getBlockManager().setTemporaryBlock(world, pos, Blocks.EMERALD_BLOCK.getDefaultState());
                    it.remove();
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return this.passedPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        ServerWorld world = game.getWorld();

        // 1. Remove Crossbows & Arrows from player inventories
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack stack = p.getInventory().getStack(i);
                if (stack.isOf(Items.CROSSBOW) || stack.isOf(Items.ARROW)) {
                    p.getInventory().setStack(i, ItemStack.EMPTY);
                }
            }
        }

        // 2. Discard any in-flight arrows in the arena
        if (world != null) {
            MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
            int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
            Box arenaBox = new Box(bounds.minX() - 5, floorY - 2, bounds.minZ() - 5, bounds.maxX() + 5, floorY + 15, bounds.maxZ() + 5);
            world.getEntitiesByClass(PersistentProjectileEntity.class, arenaBox, Entity::isAlive).forEach(Entity::discard);

            // 3. Restore all target blocks to original air
            game.getBlockManager().restoreAll(world);
        }

        this.activeTargetPositions.clear();
        this.passedPlayers.clear();
    }
}
