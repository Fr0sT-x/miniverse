package dev.frost.miniverse.minigame.core.rules;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.rule.GameRules;

public record GlobalMatchRules(boolean keepInventory, boolean pvpEnabled) {
    public static GlobalMatchRules defaults() {
        return new GlobalMatchRules(true, false);
    }

    public void apply(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            world.getGameRules().setValue(GameRules.KEEP_INVENTORY, this.keepInventory, server);
            world.getGameRules().setValue(GameRules.PVP, this.pvpEnabled, server);
        }
    }
}
