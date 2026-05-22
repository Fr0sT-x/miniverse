package dev.frost.miniverse.minigame.core.rules;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;

public record GlobalMatchRules(boolean keepInventory, boolean pvpEnabled) {
    public static GlobalMatchRules defaults() {
        return new GlobalMatchRules(true, false);
    }

    public void apply(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            world.getGameRules().get(GameRules.KEEP_INVENTORY).set(this.keepInventory, server);
        }
        server.setPvpEnabled(this.pvpEnabled);
    }
}
