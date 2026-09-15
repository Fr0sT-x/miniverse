package dev.frost.miniverse.minigame.impl.horde.bounty;

import dev.frost.miniverse.minigame.impl.horde.HordeSurvivalMinigame;
import dev.frost.miniverse.minigame.impl.horde.wave.WaveEngine;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HordeBountyManager {
    private final HordeSurvivalMinigame minigame;
    private final Random random = new Random();
    private final Map<UUID, ActiveBounty> activeBounties = new ConcurrentHashMap<>();

    public record BountyTemplate(
        String id,
        String title,
        int targetCount,
        int rewardCoins,
        int minWave,
        boolean isCoopOnly,
        BountyType type
    ) {}

    public enum BountyType {
        KILL_ZOMBIE,
        KILL_SKELETON,
        KILL_SPIDER,
        KILL_CREEPER,
        KILL_MINER,
        KILL_HARPOON,
        KILL_SAPPER,
        FEED_TRANSMITTER,
        REVIVE_TEAMMATE
    }

    public static class ActiveBounty {
        public final BountyTemplate template;
        public int currentCount;
        public boolean completed;

        public ActiveBounty(BountyTemplate template) {
            this.template = template;
            this.currentCount = 0;
            this.completed = false;
        }
    }

    private static final List<BountyTemplate> ALL_BOUNTIES = List.of(
        new BountyTemplate("zombies", "Slay 8 Zombies", 8, 50, 1, false, BountyType.KILL_ZOMBIE),
        new BountyTemplate("battery", "Harvest 10 Fuel in Ring", 10, 75, 1, false, BountyType.FEED_TRANSMITTER),
        new BountyTemplate("skeletons", "Slay 5 Skeletons", 5, 60, 1, false, BountyType.KILL_SKELETON),
        new BountyTemplate("spiders", "Slay 4 Spiders", 4, 60, 1, false, BountyType.KILL_SPIDER),
        new BountyTemplate("creepers", "Slay 3 Creepers", 3, 70, 2, false, BountyType.KILL_CREEPER),
        new BountyTemplate("miners", "Slay 2 Miner Zombies", 2, 80, 3, false, BountyType.KILL_MINER),
        new BountyTemplate("harpoons", "Slay 2 Harpoon Drowned", 2, 85, 4, false, BountyType.KILL_HARPOON),
        new BountyTemplate("sappers", "Slay 2 Sapper Creepers", 2, 90, 5, false, BountyType.KILL_SAPPER),
        new BountyTemplate("medic", "Revive a Downed Teammate", 1, 100, 1, true, BountyType.REVIVE_TEAMMATE)
    );

    public HordeBountyManager(HordeSurvivalMinigame minigame) {
        this.minigame = minigame;
    }

    public void assignBountiesForWave(int wave, dev.frost.miniverse.minigame.impl.horde.wave.WaveDefinition def, List<ServerPlayerEntity> survivors) {
        boolean isCoop = survivors.size() > 1;
        List<BountyTemplate> eligible = new ArrayList<>();

        for (BountyTemplate b : ALL_BOUNTIES) {
            if (isBountyPossibleForWave(b, wave, def, isCoop)) {
                eligible.add(b);
            }
        }

        if (eligible.isEmpty()) return;

        for (ServerPlayerEntity player : survivors) {
            ActiveBounty existing = this.activeBounties.get(player.getUuid());
            // Assign new bounty if player has none, completed previous, or their previous bounty is impossible in current wave
            if (existing == null || existing.completed || !isBountyPossibleForWave(existing.template, wave, def, isCoop)) {
                BountyTemplate picked = eligible.get(random.nextInt(eligible.size()));
                ActiveBounty newBounty = new ActiveBounty(picked);
                this.activeBounties.put(player.getUuid(), newBounty);

                player.sendMessage(Text.literal("📜 New Bounty: " + picked.title() + " (§6+" + picked.rewardCoins() + " coins§r)").formatted(Formatting.GOLD), false);
            }
        }
    }

    public boolean isBountyPossibleForWave(BountyTemplate template, int wave, dev.frost.miniverse.minigame.impl.horde.wave.WaveDefinition def, boolean isCoop) {
        if (template.isCoopOnly() && !isCoop) {
            return false;
        }
        if (template.minWave() > wave) {
            return false;
        }
        if (def == null) {
            return template.minWave() <= wave;
        }

        return switch (template.type()) {
            case KILL_ZOMBIE -> true;
            case FEED_TRANSMITTER -> true;
            case REVIVE_TEAMMATE -> isCoop;
            case KILL_SKELETON -> def.skeletonChance() > 0.0;
            case KILL_SPIDER -> def.spiderChance() > 0.0;
            case KILL_CREEPER -> def.creeperChance() > 0.0 || def.sapperChance() > 0.0;
            case KILL_MINER -> def.minerChance() > 0.0;
            case KILL_HARPOON -> def.harpoonChance() > 0.0;
            case KILL_SAPPER -> def.sapperChance() > 0.0;
        };
    }

    public void onMobKilled(UUID killerUuid, WaveEngine.SpawnType spawnType) {
        if (killerUuid == null || spawnType == null) return;
        ActiveBounty bounty = this.activeBounties.get(killerUuid);
        if (bounty == null || bounty.completed) return;

        boolean matches = switch (bounty.template.type()) {
            case KILL_ZOMBIE -> spawnType == WaveEngine.SpawnType.ZOMBIE || spawnType == WaveEngine.SpawnType.MINER_ZOMBIE;
            case KILL_SKELETON -> spawnType == WaveEngine.SpawnType.SKELETON;
            case KILL_SPIDER -> spawnType == WaveEngine.SpawnType.SPIDER;
            case KILL_CREEPER -> spawnType == WaveEngine.SpawnType.CREEPER || spawnType == WaveEngine.SpawnType.SAPPER_CREEPER;
            case KILL_MINER -> spawnType == WaveEngine.SpawnType.MINER_ZOMBIE;
            case KILL_HARPOON -> spawnType == WaveEngine.SpawnType.HARPOON_DROWNED;
            case KILL_SAPPER -> spawnType == WaveEngine.SpawnType.SAPPER_CREEPER;
            default -> false;
        };

        if (matches) {
            incrementProgress(killerUuid, bounty);
        }
    }

    public void onFuelHarvested(UUID playerUuid, int amount) {
        if (playerUuid == null) return;
        ActiveBounty bounty = this.activeBounties.get(playerUuid);
        if (bounty == null || bounty.completed) return;

        if (bounty.template.type() == BountyType.FEED_TRANSMITTER) {
            for (int i = 0; i < amount; i++) {
                if (bounty.completed) break;
                incrementProgress(playerUuid, bounty);
            }
        }
    }

    public void onTeammateRevived(UUID playerUuid) {
        if (playerUuid == null) return;
        ActiveBounty bounty = this.activeBounties.get(playerUuid);
        if (bounty == null || bounty.completed) return;

        if (bounty.template.type() == BountyType.REVIVE_TEAMMATE) {
            incrementProgress(playerUuid, bounty);
        }
    }

    private void incrementProgress(UUID playerUuid, ActiveBounty bounty) {
        bounty.currentCount++;
        ServerPlayerEntity player = this.minigame.getServer() != null ? this.minigame.getServer().getPlayerManager().getPlayer(playerUuid) : null;

        if (bounty.currentCount >= bounty.template.targetCount()) {
            bounty.completed = true;
            this.minigame.addCoins(playerUuid, bounty.template.rewardCoins());

            if (player != null) {
                player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.2f);
                this.minigame.broadcast(Text.literal("✔ " + player.getName().getString() + " completed Bounty: " + bounty.template.title() + "! (§6+" + bounty.template.rewardCoins() + " Coins§a)").formatted(Formatting.GREEN, Formatting.BOLD));
            }
        } else {
            if (player != null) {
                player.sendMessage(Text.literal("📜 Bounty Progress: " + bounty.template.title() + " [" + bounty.currentCount + "/" + bounty.template.targetCount() + "]").formatted(Formatting.YELLOW), true);
            }
        }
    }

    public String getBountySummary(UUID playerUuid) {
        ActiveBounty b = this.activeBounties.get(playerUuid);
        if (b == null) return "None";
        if (b.completed) return "§aCompleted!";
        return b.template.title() + " §7(" + b.currentCount + "/" + b.template.targetCount() + ")";
    }

    public void cleanup() {
        this.activeBounties.clear();
    }
}
