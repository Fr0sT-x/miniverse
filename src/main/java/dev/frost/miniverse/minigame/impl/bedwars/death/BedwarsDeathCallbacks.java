package dev.frost.miniverse.minigame.impl.bedwars.death;

import dev.frost.miniverse.minigame.core.death.CancellationReason;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.DeathState;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.impl.bedwars.BedTeamState;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsDefinition;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMinigame;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    public void onDeathProcessed(ServerPlayerEntity player, DeathContext context) {
        this.config.setPendingContext(context);
    }

    @Override
    public void onRespawnComplete(ServerPlayerEntity player, DeathContext context) {
        // 1. Degrade tools
        dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsPlayerToolState toolState = minigame.getShopManager().getToolState(player.getUuid());
        toolState.degrade();
        
        // 2. Clear inventory
        player.getInventory().clear();
        minigame.equipBaseArmor(player);
        
        // 3. Re-equip armour at purchased tier
        int armorTier = toolState.getArmorTier();
        if (armorTier >= 1) {
            player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new net.minecraft.item.ItemStack(net.minecraft.item.Items.CHAINMAIL_LEGGINGS));
            player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new net.minecraft.item.ItemStack(net.minecraft.item.Items.CHAINMAIL_BOOTS));
        }
        if (armorTier >= 2) {
            player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_LEGGINGS));
            player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_BOOTS));
        }
        if (armorTier >= 3) {
            player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_LEGGINGS));
            player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_BOOTS));
        }
        
        // 4. Re-apply team upgrade enchantments
        if (context.victimTeamId() != null) {
            minigame.getUpgradeManager().applyToPlayer(player, context.victimTeamId());
        }
        // 5. Give tools at current tier and sword
        player.getInventory().offerOrDrop(new net.minecraft.item.ItemStack(net.minecraft.item.Items.WOODEN_SWORD));
        if (toolState.getPickaxeTier() > 0) {
            player.getInventory().offerOrDrop(toolState.buildPickaxe(player.getWorld().getRegistryManager()));
        }
        if (toolState.getAxeTier() > 0) {
            player.getInventory().offerOrDrop(toolState.buildAxe(player.getWorld().getRegistryManager()));
        }
        if (toolState.hasKnockbackStick()) {
            player.getInventory().offerOrDrop(dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsShopItem.KNOCKBACK_STICK.buildStack(player.getWorld().getRegistryManager()));
        }
        
        // 6. Apply hotbar layout preference (F23)
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.applyLayout(player, BedwarsDefinition.ID, java.util.List.of());
        
        // 7. Brief invincibility
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 4, false, false));
    }

    @Override
    public void onSpectatorEnter(ServerPlayerEntity player, DeathContext context) {
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
