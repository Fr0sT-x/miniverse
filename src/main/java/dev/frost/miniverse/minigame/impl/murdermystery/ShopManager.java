package dev.frost.miniverse.minigame.impl.murdermystery;

import dev.frost.miniverse.map.MapPosition;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import dev.frost.miniverse.minigame.core.role.RoleManager;
import dev.frost.miniverse.minigame.impl.murdermystery.role.MurdererRole;

public class ShopManager {
    private final VirtualEconomyManager economy;
    private final MurderMysteryWeaponManager weaponManager;
    private final RoleManager roleManager;
    private final MurderMysterySettings settings;
    private final List<java.util.UUID> shopNpcIds = new ArrayList<>();

    public ShopManager(VirtualEconomyManager economy, MurderMysteryWeaponManager weaponManager, RoleManager roleManager, MurderMysterySettings settings) {
        this.economy = economy;
        this.weaponManager = weaponManager;
        this.roleManager = roleManager;
        this.settings = settings;
    }

    public void spawnNpcs(ServerWorld world, List<MapPosition> locations) {
        for (MapPosition loc : locations) {
            VillagerEntity npc = new VillagerEntity(EntityType.VILLAGER, world);
            npc.setPosition(loc.x(), loc.y(), loc.z());
            npc.setCustomName(Text.literal("Shop"));
            npc.setCustomNameVisible(true);
            npc.setAiDisabled(true);
            npc.setInvulnerable(true);
            npc.setSilent(true);
            world.spawnEntity(npc);
            shopNpcIds.add(npc.getUuid());
        }
    }

    public boolean handleInteract(ServerPlayerEntity player, net.minecraft.entity.Entity entity) {
        if (shopNpcIds.contains(entity.getUuid())) {
            openShopGui(player);
            return true;
        }
        return false;
    }

    private void openShopGui(ServerPlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(9);
        
        ItemStack bow = new ItemStack(Items.BOW);
        bow.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Detective's Bow - " + settings.detectiveBowPrice() + " Coins"));
        inventory.setStack(0, bow);
        
        ItemStack speed = new ItemStack(Items.SUGAR);
        speed.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Speed Boost - 5 Coins"));
        inventory.setStack(1, speed);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, p) -> new net.minecraft.screen.GenericContainerScreenHandler(net.minecraft.screen.ScreenHandlerType.GENERIC_9X1, syncId, playerInv, inventory, 1) {
                @Override
                public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, net.minecraft.entity.player.PlayerEntity p2) {
                    if (slotIndex >= 0 && slotIndex < 9) {
                        if (actionType == net.minecraft.screen.slot.SlotActionType.PICKUP) {
                            handlePurchase((ServerPlayerEntity) p2, slotIndex);
                        }
                        this.sendContentUpdates();
                    } else if (actionType == net.minecraft.screen.slot.SlotActionType.QUICK_MOVE || actionType == net.minecraft.screen.slot.SlotActionType.SWAP) {
                        this.sendContentUpdates();
                    } else {
                        super.onSlotClick(slotIndex, button, actionType, p2);
                    }
                }
            },
            Text.literal("Shop (Your Coins: " + economy.getBalance(player) + ")")
        ));
    }
    
    public boolean handlePurchase(ServerPlayerEntity player, int slotId) {
        if (slotId == 0) {
            if (roleManager.hasRole(player, MurdererRole.class)) {
                player.sendMessage(Text.literal("Murderers cannot buy the Detective's Bow!").formatted(net.minecraft.util.Formatting.RED), false);
                return false;
            }
            if (economy.spendCoins(player, settings.detectiveBowPrice())) {
                weaponManager.giveDetectiveWeapon(player);
                player.sendMessage(Text.literal("Purchased Detective's Bow!").formatted(net.minecraft.util.Formatting.GREEN), false);
                return true;
            } else {
                player.sendMessage(Text.literal("Not enough coins! Price: " + settings.detectiveBowPrice() + " (You have " + economy.getBalance(player) + ")").formatted(net.minecraft.util.Formatting.RED), false);
            }
        } else if (slotId == 1) {
            if (economy.spendCoins(player, 5)) {
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SPEED, 200, 1));
                player.sendMessage(Text.literal("Purchased Speed Boost!").formatted(net.minecraft.util.Formatting.GREEN), false);
                return true;
            } else {
                player.sendMessage(Text.literal("Not enough coins! Price: 5 (You have " + economy.getBalance(player) + ")").formatted(net.minecraft.util.Formatting.RED), false);
            }
        }
        return false;
    }

    public void clear(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            for (java.util.UUID uuid : shopNpcIds) {
                for (ServerWorld world : server.getWorlds()) {
                    net.minecraft.entity.Entity e = world.getEntity(uuid);
                    if (e != null && !e.isRemoved()) {
                        e.discard();
                    }
                }
            }
        }
        shopNpcIds.clear();
    }

    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (java.util.UUID uuid : shopNpcIds) {
            array.add(uuid.toString());
        }
        json.add("shopNpcIds", array);
        return json;
    }

    public void loadRuntimeState(com.google.gson.JsonObject json) {
        shopNpcIds.clear();
        if (json.has("shopNpcIds")) {
            for (com.google.gson.JsonElement elem : json.getAsJsonArray("shopNpcIds")) {
                shopNpcIds.add(java.util.UUID.fromString(elem.getAsString()));
            }
        }
    }
}
