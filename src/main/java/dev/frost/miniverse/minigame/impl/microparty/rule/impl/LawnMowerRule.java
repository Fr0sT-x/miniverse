package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyArenaHelper;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
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
    public Text instruction(MicroFrenzyMinigame game) {
        int req = getRequiredCuts(game);
        return Text.literal("Mow " + req + " flowers or grass!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    public static int getRequiredCuts(MicroFrenzyMinigame game) {
        if (game == null) return 3;
        float factor = game.getSpeedFactor();
        if (factor >= 0.9f) return 3;
        if (factor >= 0.7f) return 2;
        return 1;
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        this.mowedCount.clear();
        this.plantPositions.clear();

        ServerWorld world = game.getWorld();
        if (world == null) return;

        int floorY = MicroFrenzyArenaHelper.getFloorY(game.getMapConfig());
        MicroFrenzyArenaHelper.ArenaBounds2D bounds = MicroFrenzyArenaHelper.getBounds2D(game.getMapConfig());

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
    public void onTick(MicroFrenzyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null || this.plantPositions.isEmpty()) return;

        int required = getRequiredCuts(game);

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            int current = this.mowedCount.getOrDefault(p.getUuid(), 0);
            if (current >= required) continue;

            // Check if player is near any plant to mow it
            Iterator<BlockPos> it = this.plantPositions.iterator();
            while (it.hasNext()) {
                BlockPos pos = it.next();
                if (p.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= 2.25) { // 1.5 blocks radius
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.05);
                    world.playSound(null, pos, SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 0.8f, 1.2f);
                    it.remove();

                    int updated = this.mowedCount.merge(p.getUuid(), 1, Integer::sum);
                    if (updated >= required) {
                        p.sendMessage(Text.literal("§a§l✔ Lawn Mowed!"), true);
                        p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                    } else {
                        p.sendMessage(Text.literal("§aMowed: " + updated + "§7/§e" + required), true);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return this.mowedCount.getOrDefault(player.getUuid(), 0) >= getRequiredCuts(game);
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
        }
        this.plantPositions.clear();
        this.mowedCount.clear();
    }
}
