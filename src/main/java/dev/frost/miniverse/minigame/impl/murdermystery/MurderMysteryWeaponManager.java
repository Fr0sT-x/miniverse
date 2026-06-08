package dev.frost.miniverse.minigame.impl.murdermystery;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MurderMysteryWeaponManager {
    private final List<DisplayEntity.ItemDisplayEntity> droppedBows = new ArrayList<>();
    
    public void giveMurdererWeapon(ServerPlayerEntity player) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("Murderer's Knife"));
        player.getInventory().setStack(8, sword);
    }
    
    public void giveDetectiveWeapon(ServerPlayerEntity player) {
        ItemStack bow = new ItemStack(Items.BOW);
        bow.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("Detective's Bow"));
        player.getInventory().insertStack(bow);
        player.getInventory().insertStack(new ItemStack(Items.ARROW, 1));
    }
    
    public void dropDetectiveWeapon(ServerWorld world, Vec3d pos) {
        DisplayEntity.ItemDisplayEntity bow = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        bow.setPosition(pos.x, pos.y + 0.5, pos.z);
        bow.setItemStack(new ItemStack(Items.BOW));
        world.spawnEntity(bow);
        droppedBows.add(bow);
    }
    
    public ServerPlayerEntity checkBowPickup(List<ServerPlayerEntity> eligiblePlayers) {
        Iterator<DisplayEntity.ItemDisplayEntity> iterator = droppedBows.iterator();
        while (iterator.hasNext()) {
            DisplayEntity.ItemDisplayEntity bow = iterator.next();
            if (bow.isRemoved()) {
                iterator.remove();
                continue;
            }
            Box hitbox = bow.getBoundingBox().expand(1.5);
            for (ServerPlayerEntity player : eligiblePlayers) {
                if (hitbox.intersects(player.getBoundingBox())) {
                    bow.discard();
                    iterator.remove();
                    return player;
                }
            }
        }
        return null;
    }

    public void clear() {
        for (DisplayEntity.ItemDisplayEntity bow : droppedBows) {
            if (bow != null && !bow.isRemoved()) {
                bow.discard();
            }
        }
        droppedBows.clear();
    }
}
