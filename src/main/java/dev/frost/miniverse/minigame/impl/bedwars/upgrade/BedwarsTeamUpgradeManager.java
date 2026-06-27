package dev.frost.miniverse.minigame.impl.bedwars.upgrade;

import dev.frost.miniverse.map.MapPosition;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BedwarsTeamUpgradeManager {
    private final Map<String, int[]> upgradeTiers = new ConcurrentHashMap<>();
    private final List<UUID> upgradeNpcIds = new ArrayList<>();
    private final dev.frost.miniverse.minigame.impl.bedwars.BedwarsMinigame minigame;
    
    public BedwarsTeamUpgradeManager(java.util.Set<String> teamIds, dev.frost.miniverse.minigame.impl.bedwars.BedwarsMinigame minigame) {
        this.minigame = minigame;
        for (String teamId : teamIds) {
            upgradeTiers.put(teamId, new int[BedwarsTeamUpgrade.values().length]);
        }
    }

    public void spawnNpcs(ServerWorld world, List<MapPosition> locations) {
        for (MapPosition loc : locations) {
            VillagerEntity npc = new VillagerEntity(EntityType.VILLAGER, world);
            npc.setPosition(loc.x(), loc.y(), loc.z());
            npc.setCustomName(Text.literal("Team Upgrades"));
            npc.setCustomNameVisible(true);
            npc.setAiDisabled(true);
            npc.setInvulnerable(true);
            npc.setSilent(true);
            world.spawnEntity(npc);
            upgradeNpcIds.add(npc.getUuid());
        }
    }

    public boolean handleInteract(ServerPlayerEntity player, net.minecraft.entity.Entity entity) {
        if (upgradeNpcIds.contains(entity.getUuid())) {
            String teamId = this.minigame.teamManager().teamId(player.getUuid());
            if (teamId != null) {
                BedwarsUpgradeGui.open(player, this, teamId);
            } else {
                player.sendMessage(Text.literal("You are not in a team!").formatted(net.minecraft.util.Formatting.RED), false);
            }
            return true;
        }
        return false;
    }

    public void purchase(String teamId, BedwarsTeamUpgrade upgrade) {
        int[] tiers = upgradeTiers.get(teamId);
        if (tiers != null) {
            int currentTier = tiers[upgrade.ordinal()];
            if (currentTier < upgrade.tierCosts().length) {
                tiers[upgrade.ordinal()] = currentTier + 1;
                
                // Handle instant upgrade effects
                if (upgrade == BedwarsTeamUpgrade.FORGE) {
                    double[] modifiers = {1.0, 0.75, 0.5, 0.25, 0.15};
                    if (minigame.getGeneratorManager() != null) {
                        minigame.getGeneratorManager().setTeamForgeMultiplier(teamId, modifiers[currentTier + 1]);
                    }
                }
            }
        }
    }

    public int getTier(String teamId, BedwarsTeamUpgrade upgrade) {
        int[] tiers = upgradeTiers.get(teamId);
        return tiers != null ? tiers[upgrade.ordinal()] : 0;
    }

    public void applyToPlayer(ServerPlayerEntity player, String teamId) {
        int[] tiers = upgradeTiers.get(teamId);
        if (tiers == null) return;

        int hasteTier = tiers[BedwarsTeamUpgrade.HASTE.ordinal()];
        if (hasteTier > 0) {
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.HASTE, -1, hasteTier - 1, false, false));
        }
    }

    public void clear(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            for (UUID uuid : upgradeNpcIds) {
                for (ServerWorld world : server.getWorlds()) {
                    net.minecraft.entity.Entity e = world.getEntity(uuid);
                    if (e != null && !e.isRemoved()) {
                        e.discard();
                    }
                }
            }
        }
        upgradeNpcIds.clear();
    }
}
