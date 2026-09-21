package dev.frost.miniverse.minigame.impl.bedwars.death;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.visibility.TeamGlowVisibility;
import dev.frost.miniverse.minigame.impl.bedwars.BedTeamState;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsDefinition;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMinigame;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

public final class BedwarsDeathCallbacks implements DeathLifecycleCallbacks {
    private final BedwarsMinigame minigame;
    private final BedwarsDeathLifecycleConfig config;
    private final Map<String, BedTeamState> bedTeamStates;
    private final Set<UUID> permanentlyEliminated;

    public BedwarsDeathCallbacks(BedwarsMinigame minigame, BedwarsDeathLifecycleConfig config, Map<String, BedTeamState> bedTeamStates, Set<UUID> permanentlyEliminated) {
        this.minigame = minigame;
        this.config = config;
        this.bedTeamStates = bedTeamStates;
        this.permanentlyEliminated = permanentlyEliminated;
    }

    @Override
    public void onDeath(ServerPlayerEntity player, DeathContext context) {
        if (context.killer() instanceof ServerPlayerEntity killer) {
            net.minecraft.entity.player.PlayerInventory victimInv = player.getInventory();
            
            int iron = 0;
            int gold = 0;
            int diamond = 0;
            int emerald = 0;
            
            for (int i = 0; i < victimInv.size(); i++) {
                net.minecraft.item.ItemStack stack = victimInv.getStack(i);
                if (!stack.isEmpty()) {
                    net.minecraft.item.Item item = stack.getItem();
                    if (item == net.minecraft.item.Items.IRON_INGOT) {
                        iron += stack.getCount();
                    } else if (item == net.minecraft.item.Items.GOLD_INGOT) {
                        gold += stack.getCount();
                    } else if (item == net.minecraft.item.Items.DIAMOND) {
                        diamond += stack.getCount();
                    } else if (item == net.minecraft.item.Items.EMERALD) {
                        emerald += stack.getCount();
                    } else {
                        continue;
                    }
                    
                    net.minecraft.item.ItemStack copy = stack.copy();
                    killer.getInventory().insertStack(copy);
                    if (!copy.isEmpty()) {
                        killer.dropItem(copy, false, true);
                    }
                    victimInv.setStack(i, net.minecraft.item.ItemStack.EMPTY);
                }
            }
            
            if (iron > 0 || gold > 0 || diamond > 0 || emerald > 0) {
                net.minecraft.text.MutableText msg = net.minecraft.text.Text.literal("+ ");
                boolean first = true;
                if (iron > 0) {
                    msg.append(net.minecraft.text.Text.literal(iron + " Iron").formatted(net.minecraft.util.Formatting.WHITE));
                    first = false;
                }
                if (gold > 0) {
                    if (!first) msg.append(net.minecraft.text.Text.literal(", ").formatted(net.minecraft.util.Formatting.GRAY));
                    msg.append(net.minecraft.text.Text.literal(gold + " Gold").formatted(net.minecraft.util.Formatting.GOLD));
                    first = false;
                }
                if (diamond > 0) {
                    if (!first) msg.append(net.minecraft.text.Text.literal(", ").formatted(net.minecraft.util.Formatting.GRAY));
                    msg.append(net.minecraft.text.Text.literal(diamond + " Diamond").formatted(net.minecraft.util.Formatting.AQUA));
                    first = false;
                }
                if (emerald > 0) {
                    if (!first) msg.append(net.minecraft.text.Text.literal(", ").formatted(net.minecraft.util.Formatting.GRAY));
                    msg.append(net.minecraft.text.Text.literal(emerald + " Emerald").formatted(net.minecraft.util.Formatting.GREEN));
                }
                killer.sendMessage(msg, false);
            }
        }
    }

    @Override
    public void onRespawnComplete(ServerPlayerEntity player, DeathContext context) {
        // 1. Degrade tools
        dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsPlayerToolState toolState = minigame.getShopManager().getToolState(player.getUuid());
        toolState.degrade();
        
        // 2. Gather items to give
        java.util.List<net.minecraft.item.ItemStack> itemsToGive = new java.util.ArrayList<>();
        net.minecraft.item.ItemStack sword = new net.minecraft.item.ItemStack(net.minecraft.item.Items.WOODEN_SWORD);
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(sword, "BEDWARS_SWORD");
        itemsToGive.add(sword);
        if (toolState.getPickaxeTier() > 0) {
            net.minecraft.item.ItemStack pickaxe = toolState.buildPickaxe(player.getWorld().getRegistryManager());
            dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(pickaxe, "BEDWARS_PICKAXE");
            itemsToGive.add(pickaxe);
        }
        if (toolState.getAxeTier() > 0) {
            net.minecraft.item.ItemStack axe = toolState.buildAxe(player.getWorld().getRegistryManager());
            dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(axe, "BEDWARS_AXE");
            itemsToGive.add(axe);
        }
        if (toolState.hasKnockbackStick()) {
            net.minecraft.item.ItemStack stick = new net.minecraft.item.ItemStack(net.minecraft.item.Items.STICK);
            stick.addEnchantment(player.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT).getEntry(net.minecraft.enchantment.Enchantments.KNOCKBACK).get(), 1);
            dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(stick, "BEDWARS_STICK");
            itemsToGive.add(stick);
        }
        
        // 3. Apply hotbar layout preference (F23) - this clears the inventory including armor
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.applyLayout(player, BedwarsDefinition.ID, itemsToGive);

        // 4. Equip armor
        minigame.equipBaseArmor(player);
        int armorTier = toolState.getArmorTier();
        dev.frost.miniverse.minigame.impl.bedwars.shop.items.ArmorUpgradeItem.equipArmor(player, armorTier);
        
        // 5. Re-apply team upgrade enchantments
        if (context.victimTeamId() != null) {
            minigame.getUpgradeManager().applyToPlayer(player, context.victimTeamId());
        }
        
        // 7. Brief invincibility
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 4, false, false));
        TeamGlowVisibility.resyncFor(player);
    }

    @Override
    public void onSpectatorEnter(ServerPlayerEntity player, DeathContext context) {
        TeamGlowVisibility.resyncFor(player);

        String teamId = context.victimTeamId();
        boolean bedGone = teamId != null
            && bedTeamStates.containsKey(teamId)
            && !bedTeamStates.get(teamId).isBedAlive();

        if (bedGone) {
            permanentlyEliminated.add(player.getUuid());
            this.minigame.checkWinCondition();
        }
    }
}
