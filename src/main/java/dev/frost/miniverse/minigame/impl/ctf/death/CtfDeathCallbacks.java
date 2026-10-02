package dev.frost.miniverse.minigame.impl.ctf.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework;
import dev.frost.miniverse.minigame.core.visibility.TeamGlowVisibility;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagDefinition;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagMinigame;
import dev.frost.miniverse.minigame.impl.ctf.economy.CtfPlayerUpgradeState;
import dev.frost.miniverse.minigame.impl.ctf.flag.CtfFlagInstance;
import dev.frost.miniverse.minigame.impl.ctf.flag.CtfFlagState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CtfDeathCallbacks implements DeathLifecycleCallbacks {
    private final CaptureTheFlagMinigame minigame;
    private final Set<UUID> permanentlyEliminated;

    public CtfDeathCallbacks(CaptureTheFlagMinigame minigame, Set<UUID> permanentlyEliminated) {
        this.minigame = minigame;
        this.permanentlyEliminated = permanentlyEliminated;
    }

    @Override
    public void onDeath(ServerPlayerEntity player, DeathContext context) {
        // 1. Drop carried flag
        boolean isVoid = context.damageSource() != null && context.damageSource().isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD);
        this.minigame.getFlagManager().dropCarriedFlag(player, isVoid);

        // 2. Economy kill bounty
        if (context.killer() instanceof ServerPlayerEntity killer && !killer.getUuid().equals(player.getUuid())) {
            this.minigame.getEconomyManager().rewardKill(killer, player);
        }
    }

    @Override
    public void onRespawnComplete(ServerPlayerEntity player, DeathContext context) {
        CtfPlayerUpgradeState upgradeState = this.minigame.getEconomyManager().getUpgradeState(player.getUuid());

        // 1. Gather kit items
        List<ItemStack> itemsToGive = new ArrayList<>();

        // Sword
        ItemStack sword = upgradeState.buildSword(player);
        InventoryLayoutFramework.tagKitItem(sword, "CTF_SWORD");
        itemsToGive.add(sword);

        // Bow & Arrows
        ItemStack bow = upgradeState.buildBow(player);
        InventoryLayoutFramework.tagKitItem(bow, "CTF_BOW");
        itemsToGive.add(bow);

        ItemStack arrows = new ItemStack(Items.ARROW, 12);
        InventoryLayoutFramework.tagKitItem(arrows, "CTF_ARROWS");
        itemsToGive.add(arrows);

        // Shears / Axe if unlocked
        if (upgradeState.hasShears()) {
            ItemStack shears = new ItemStack(Items.SHEARS);
            InventoryLayoutFramework.tagKitItem(shears, "CTF_SHEARS");
            itemsToGive.add(shears);
        }
        if (upgradeState.hasAxe()) {
            ItemStack axe = new ItemStack(Items.STONE_AXE);
            InventoryLayoutFramework.tagKitItem(axe, "CTF_AXE");
            itemsToGive.add(axe);
        }

        // Team wool
        ItemStack wool = new ItemStack(this.getTeamWoolItem(context.victimTeamId()), 32);
        InventoryLayoutFramework.tagKitItem(wool, "CTF_BLOCKS");
        itemsToGive.add(wool);

        // Compass
        ItemStack compass = CaptureTheFlagMinigame.createCompass();
        itemsToGive.add(compass);

        // 2. Apply hotbar layout preference (F23)
        InventoryLayoutFramework.applyLayout(player, CaptureTheFlagDefinition.ID, itemsToGive);

        // 3. Equip Armor
        Formatting teamColor = this.minigame.getPlayerTeamColor(player);
        upgradeState.equipArmor(player, teamColor);

        // 4. Brief spawn protection
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 4, false, false));
        TeamGlowVisibility.resyncFor(player);
    }

    @Override
    public void onSpectatorEnter(ServerPlayerEntity player, DeathContext context) {
        TeamGlowVisibility.resyncFor(player);

        String teamId = context.victimTeamId();
        if (teamId != null) {
            CtfFlagInstance flag = this.minigame.getFlagManager().getFlag(teamId);
            boolean flagGone = flag != null && flag.state() == CtfFlagState.CAPTURED;
            if (flagGone && this.minigame.getSettings().eliminationMode()) {
                this.permanentlyEliminated.add(player.getUuid());
                this.minigame.checkWinCondition();
            }
        }
    }

    private Item getTeamWoolItem(String teamId) {
        Formatting color = this.minigame.getTeamColor(teamId);
        if (color == null) return Items.WHITE_WOOL;
        return switch (color) {
            case RED, DARK_RED -> Items.RED_WOOL;
            case BLUE, DARK_BLUE -> Items.BLUE_WOOL;
            case GREEN, DARK_GREEN -> Items.LIME_WOOL;
            case YELLOW, GOLD -> Items.YELLOW_WOOL;
            case AQUA, DARK_AQUA -> Items.LIGHT_BLUE_WOOL;
            case LIGHT_PURPLE, DARK_PURPLE -> Items.MAGENTA_WOOL;
            case GRAY, DARK_GRAY -> Items.GRAY_WOOL;
            default -> Items.WHITE_WOOL;
        };
    }
}
