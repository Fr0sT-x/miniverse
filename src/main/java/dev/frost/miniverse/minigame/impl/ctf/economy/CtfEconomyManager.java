package dev.frost.miniverse.minigame.impl.ctf.economy;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CtfEconomyManager {
    private final Map<UUID, Integer> coins = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> gems = new ConcurrentHashMap<>();
    private final Map<UUID, CtfPlayerUpgradeState> upgradeStates = new ConcurrentHashMap<>();

    public int getCoins(UUID uuid) {
        return coins.getOrDefault(uuid, 0);
    }

    public int getGems(UUID uuid) {
        return gems.getOrDefault(uuid, 0);
    }

    public void addCoins(ServerPlayerEntity player, int amount) {
        if (amount <= 0 || player == null) return;
        coins.merge(player.getUuid(), amount, Integer::sum);
        player.sendMessage(Text.literal("+" + amount + " Coins").formatted(Formatting.GOLD), true);
    }

    public void addGems(ServerPlayerEntity player, int amount) {
        if (amount <= 0 || player == null) return;
        gems.merge(player.getUuid(), amount, Integer::sum);
        player.sendMessage(Text.literal("+" + amount + " Gems").formatted(Formatting.GREEN), true);
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.5F);
    }

    public boolean deductCoins(ServerPlayerEntity player, int amount) {
        if (player == null) return false;
        int current = getCoins(player.getUuid());
        if (current < amount) return false;
        coins.put(player.getUuid(), current - amount);
        return true;
    }

    public boolean deductGems(ServerPlayerEntity player, int amount) {
        if (player == null) return false;
        int current = getGems(player.getUuid());
        if (current < amount) return false;
        gems.put(player.getUuid(), current - amount);
        return true;
    }

    public void rewardKill(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        if (killer == null) return;
        // Kill rewards: 12 coins, 6 gems (+33% of victim's current wallet)
        int bonusCoins = 12;
        int bonusGems = 6;
        if (victim != null) {
            int victimCoins = getCoins(victim.getUuid());
            int victimGems = getGems(victim.getUuid());
            int stolenCoins = victimCoins / 3;
            int stolenGems = victimGems / 3;
            coins.put(victim.getUuid(), Math.max(0, victimCoins - stolenCoins));
            gems.put(victim.getUuid(), Math.max(0, victimGems - stolenGems));
            bonusCoins += stolenCoins;
            bonusGems += stolenGems;
        }
        addCoins(killer, bonusCoins);
        addGems(killer, bonusGems);
    }

    public void rewardFlagGrab(ServerPlayerEntity player) {
        addCoins(player, 20);
        addGems(player, 8);
    }

    public void rewardFlagCapture(ServerPlayerEntity player) {
        addCoins(player, 80);
        addGems(player, 30);
    }

    public CtfPlayerUpgradeState getUpgradeState(UUID uuid) {
        return upgradeStates.computeIfAbsent(uuid, k -> new CtfPlayerUpgradeState());
    }

    public void clear() {
        coins.clear();
        gems.clear();
        upgradeStates.clear();
    }
}
