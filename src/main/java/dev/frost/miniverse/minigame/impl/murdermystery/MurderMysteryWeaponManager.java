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
    private final List<java.util.UUID> droppedBowIds = new ArrayList<>();
    
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
        droppedBowIds.add(bow.getUuid());
    }
    
    public ServerPlayerEntity checkBowPickup(List<ServerPlayerEntity> eligiblePlayers) {
        if (eligiblePlayers.isEmpty()) return null;
        ServerWorld world = eligiblePlayers.get(0).getServerWorld();
        
        Iterator<java.util.UUID> iterator = droppedBowIds.iterator();
        while (iterator.hasNext()) {
            java.util.UUID uuid = iterator.next();
            net.minecraft.entity.Entity entity = world.getEntity(uuid);
            
            if (!(entity instanceof DisplayEntity.ItemDisplayEntity bow) || bow.isRemoved()) {
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

    public void clear(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            for (java.util.UUID uuid : droppedBowIds) {
                for (ServerWorld world : server.getWorlds()) {
                    net.minecraft.entity.Entity e = world.getEntity(uuid);
                    if (e != null && !e.isRemoved()) {
                        e.discard();
                    }
                }
            }
        }
        droppedBowIds.clear();
    }

    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (java.util.UUID uuid : droppedBowIds) {
            array.add(uuid.toString());
        }
        json.add("droppedBowIds", array);
        return json;
    }

    public void loadRuntimeState(com.google.gson.JsonObject json) {
        droppedBowIds.clear();
        if (json.has("droppedBowIds")) {
            for (com.google.gson.JsonElement elem : json.getAsJsonArray("droppedBowIds")) {
                droppedBowIds.add(java.util.UUID.fromString(elem.getAsString()));
            }
        }
    }
}
