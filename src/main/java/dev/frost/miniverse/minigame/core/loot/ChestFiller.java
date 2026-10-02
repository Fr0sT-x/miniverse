package dev.frost.miniverse.minigame.core.loot;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility for populating containers (single chests, double chests, barrels, etc.)
 * with anti-collision random slot scattering.
 */
public final class ChestFiller {
    private ChestFiller() {
    }

    /**
     * Fills an Inventory with a list of ItemStacks scattered randomly across available slots.
     *
     * @param inventory the container inventory
     * @param items the items to place
     * @param random random instance
     * @param clearFirst whether to wipe existing contents before filling
     */
    public static void fill(Inventory inventory, List<ItemStack> items, Random random, boolean clearFirst) {
        if (inventory == null || items == null || items.isEmpty()) {
            return;
        }

        if (clearFirst) {
            inventory.clear();
        }

        int size = inventory.size();
        if (size <= 0) return;

        List<Integer> availableSlots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (inventory.getStack(i).isEmpty()) {
                availableSlots.add(i);
            }
        }

        Collections.shuffle(availableSlots, new java.util.Random(random.nextLong()));

        int slotIndex = 0;
        for (ItemStack item : items) {
            if (slotIndex >= availableSlots.size()) {
                break;
            }
            int slot = availableSlots.get(slotIndex++);
            inventory.setStack(slot, item.copy());
        }
        inventory.markDirty();
    }

    /**
     * Resolves the container at the given block position in the world (handling single and double chests)
     * and fills it using the provided loot table.
     *
     * @return true if a valid container was found and filled, false otherwise
     */
    public static boolean fillChestAt(ServerWorld world, BlockPos pos, ChestLootTable lootTable, Random random, boolean clearFirst) {
        if (world == null || pos == null || lootTable == null) {
            return false;
        }

        BlockState state = world.getBlockState(pos);
        Inventory inv = null;

        if (state.getBlock() instanceof ChestBlock chestBlock) {
            inv = ChestBlock.getInventory(chestBlock, state, world, pos, true);
        }

        if (inv == null && world.getBlockEntity(pos) instanceof Inventory entityInv) {
            inv = entityInv;
        }

        if (inv != null) {
            List<ItemStack> items = lootTable.roll(random, world.getRegistryManager());
            fill(inv, items, random, clearFirst);
            return true;
        }

        return false;
    }
}
