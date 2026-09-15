package dev.frost.miniverse.minigame.impl.horde.wave;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class SiegeMobManager {
    public static final String TAG_MINER = "siege_miner";
    public static final String TAG_HARPOON = "siege_harpoon";
    public static final String TAG_SAPPER = "siege_sapper";

    private SiegeMobManager() {}

    public static void applyMinerZombie(ZombieEntity zombie) {
        zombie.addCommandTag(TAG_MINER);
        zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setCustomName(Text.literal("⛏ Miner Zombie").formatted(Formatting.GOLD));
        zombie.setCustomNameVisible(true);
    }

    public static void applyHarpoonDrowned(DrownedEntity drowned) {
        drowned.addCommandTag(TAG_HARPOON);
        drowned.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
        drowned.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        drowned.setCustomName(Text.literal("⚓ Harpoon Drowned").formatted(Formatting.AQUA));
        drowned.setCustomNameVisible(true);
    }

    public static void applySapperCreeper(CreeperEntity creeper) {
        creeper.addCommandTag(TAG_SAPPER);
        creeper.setCustomName(Text.literal("💥 Sapper Creeper").formatted(Formatting.RED, Formatting.BOLD));
        creeper.setCustomNameVisible(true);
    }

    public static void tickMob(MobEntity mob, ServerWorld world, List<ServerPlayerEntity> players) {
        if (mob.getCommandTags().contains(TAG_MINER)) {
            // Check if there is a player-placed block immediately in front of it blocking path
            BlockPos targetPos = mob.getBlockPos().offset(mob.getHorizontalFacing());
            BlockState state = world.getBlockState(targetPos);
            if (isBreakableObstacle(state)) {
                world.breakBlock(targetPos, false, mob);
                world.playSound(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(), SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
            }
        } else if (mob.getCommandTags().contains(TAG_HARPOON)) {
            // Find nearby elevated player (> 3 blocks above mob)
            for (ServerPlayerEntity player : players) {
                if (player.getY() > mob.getY() + 2.5 && mob.squaredDistanceTo(player) < 225.0) {
                    if (world.getTime() % 40 == 0) {
                        // Pull player towards drowned
                        Vec3d pull = mob.getPos().subtract(player.getPos()).normalize().multiply(1.2).add(0, -0.2, 0);
                        player.setVelocity(pull);
                        player.velocityModified = true;
                        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 1.2f, 0.8f);
                        player.sendMessage(Text.literal("⚓ You were reeled in by a Harpoon Drowned!").formatted(Formatting.RED), true);
                    }
                    break;
                }
            }
        }
    }

    private static boolean isBreakableObstacle(BlockState state) {
        return state.isOf(Blocks.COBBLESTONE)
            || state.isOf(Blocks.OAK_PLANKS)
            || state.isOf(Blocks.SPRUCE_PLANKS)
            || state.isOf(Blocks.COBWEB)
            || state.isOf(Blocks.DIRT);
    }
}
