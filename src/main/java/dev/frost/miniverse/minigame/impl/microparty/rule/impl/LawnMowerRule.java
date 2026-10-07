package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class LawnMowerRule implements MicroRule {
    private final Random random = new Random();
    private final Map<UUID, Integer> mowedCount = new HashMap<>();
    private final Set<BlockPos> plantPositions = new HashSet<>();

    @Override
    public String id() {
        return "lawn_mower";
    }

    @Override
    public String name() {
        return "Mow The Lawn";
    }

    @Override
    public String description() {
        return "Mow down flowers and tall grass with your hoe.";
    }

    @Override
    public Text title() {
        return Text.literal("MOW THE LAWN!").formatted(Formatting.GREEN, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return instruction(null);
    }

    @Override
    public Text instruction(MicroPartyMinigame game) {
        int req = getRequiredCuts(game);
        return Text.literal("Mow " + req + " flowers or grass!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public double minDurationSeconds() {
        return 4.0;
    }

    public static int getRequiredCuts(MicroPartyMinigame game) {
        if (game == null) return 3;
        float factor = game.getSpeedFactor();
        if (factor >= 0.9f) return 3;
        if (factor >= 0.7f) return 2;
        return 1;
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.mowedCount.clear();
        this.plantPositions.clear();

        ServerWorld world = game.getWorld();
        if (world == null) return;

        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());

        BlockState[] plantTypes = new BlockState[] {
            Blocks.POPPY.getDefaultState(),
            Blocks.DANDELION.getDefaultState(),
            Blocks.SHORT_GRASS.getDefaultState(),
            Blocks.CORNFLOWER.getDefaultState()
        };

        int plantsToSpawn = Math.max(20, game.getLivingPlayers().size() * 6);
        int margin = Math.max(1, bounds.width() / 8);

        for (int i = 0; i < plantsToSpawn; i++) {
            int x = bounds.minX() + margin + random.nextInt(Math.max(1, bounds.width() - 2 * margin));
            int z = bounds.minZ() + margin + random.nextInt(Math.max(1, bounds.depth() - 2 * margin));
            BlockPos plantPos = new BlockPos(x, floorY, z);
            BlockPos groundPos = new BlockPos(x, floorY - 1, z);

            if (!world.getBlockState(groundPos).isAir() && world.getBlockState(plantPos).isAir()) {
                BlockState plant = plantTypes[random.nextInt(plantTypes.length)];
                game.getBlockManager().setTemporaryBlock(world, plantPos, plant);
                this.plantPositions.add(plantPos);
            }
        }

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            p.getInventory().setStack(0, new ItemStack(Items.DIAMOND_HOE));
            p.getInventory().selectedSlot = 0;
            p.currentScreenHandler.sendContentUpdates();
            this.mowedCount.put(p.getUuid(), 0);
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null || this.plantPositions.isEmpty()) return;

        int required = getRequiredCuts(game);

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            int current = this.mowedCount.getOrDefault(p.getUuid(), 0);
            if (current >= required) continue;

            // Require explicit hand swing (left-click / punch) to mow flowers
            if (!p.handSwinging) continue;

            BlockPos closestPlant = null;
            double closestDistSq = 9.0; // 3.0 blocks max reach
            for (BlockPos pos : this.plantPositions) {
                double dSq = p.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (dSq <= closestDistSq) {
                    closestDistSq = dSq;
                    closestPlant = pos;
                }
            }

            if (closestPlant != null) {
                this.plantPositions.remove(closestPlant);
                world.setBlockState(closestPlant, Blocks.AIR.getDefaultState());
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, closestPlant.getX() + 0.5, closestPlant.getY() + 0.5, closestPlant.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.05);
                world.playSound(null, closestPlant, SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 0.8f, 1.2f);

                int updated = this.mowedCount.merge(p.getUuid(), 1, Integer::sum);
                if (updated >= required) {
                    p.sendMessage(Text.literal("§a§l✔ Lawn Mowed!"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                } else {
                    p.sendMessage(Text.literal("§aMowed: " + updated + "§7/§e" + required), true);
                }
            }
        }
    }

    @Override
    public net.minecraft.util.ActionResult onUseBlock(ServerPlayerEntity player, net.minecraft.world.World world, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hitResult, MicroPartyMinigame game) {
        BlockPos pos = hitResult.getBlockPos();
        if (this.plantPositions.contains(pos)) {
            this.plantPositions.remove(pos);
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
            if (world instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.05);
            }
            player.playSoundToPlayer(SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 0.8f, 1.2f);
            int required = getRequiredCuts(game);
            int updated = this.mowedCount.merge(player.getUuid(), 1, Integer::sum);
            if (updated >= required) {
                player.sendMessage(Text.literal("§a§l✔ Lawn Mowed!"), true);
                player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
            } else {
                player.sendMessage(Text.literal("§aMowed: " + updated + "§7/§e" + required), true);
            }
            return net.minecraft.util.ActionResult.SUCCESS;
        }
        return net.minecraft.util.ActionResult.PASS;
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return this.mowedCount.getOrDefault(player.getUuid(), 0) >= getRequiredCuts(game);
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
        }
        this.plantPositions.clear();
        this.mowedCount.clear();
    }
}
