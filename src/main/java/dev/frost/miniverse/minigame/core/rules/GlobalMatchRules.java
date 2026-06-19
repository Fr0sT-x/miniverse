package dev.frost.miniverse.minigame.core.rules;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;

public record GlobalMatchRules(
    boolean keepInventory,
    boolean doImmediateRespawn,
    boolean pvpEnabled,
    boolean doDaylightCycle,
    boolean doWeatherCycle,
    boolean fallDamage,
    boolean naturalRegeneration,
    boolean announceAdvancements
) {
    public static GlobalMatchRules defaults(boolean keepInventory, boolean doImmediateRespawn) {
        return new GlobalMatchRules(keepInventory, doImmediateRespawn, true, true, true, true, true, true);
    }

    public void apply(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            world.getGameRules().get(GameRules.KEEP_INVENTORY).set(this.keepInventory, server);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(this.doDaylightCycle, server);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(this.doWeatherCycle, server);
            world.getGameRules().get(GameRules.FALL_DAMAGE).set(this.fallDamage, server);
            world.getGameRules().get(GameRules.NATURAL_REGENERATION).set(this.naturalRegeneration, server);
            world.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(this.announceAdvancements, server);
            world.getGameRules().get(GameRules.DO_IMMEDIATE_RESPAWN).set(this.doImmediateRespawn, server);
        }
        server.setPvpEnabled(this.pvpEnabled);
    }
}
