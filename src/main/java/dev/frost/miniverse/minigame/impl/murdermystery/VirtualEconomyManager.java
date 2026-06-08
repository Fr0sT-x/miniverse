package dev.frost.miniverse.minigame.impl.murdermystery;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualEconomyManager {
    private final Map<UUID, Integer> balances = new ConcurrentHashMap<>();

    public int getBalance(ServerPlayerEntity player) {
        return balances.getOrDefault(player.getUuid(), 0);
    }

    public void setBalance(ServerPlayerEntity player, int amount) {
        balances.put(player.getUuid(), Math.max(0, amount));
    }

    public void addCoins(ServerPlayerEntity player, int amount) {
        setBalance(player, getBalance(player) + amount);
    }

    public boolean spendCoins(ServerPlayerEntity player, int amount) {
        int current = getBalance(player);
        if (current >= amount) {
            setBalance(player, current - amount);
            return true;
        }
        return false;
    }

    public void clear() {
        balances.clear();
    }
}
