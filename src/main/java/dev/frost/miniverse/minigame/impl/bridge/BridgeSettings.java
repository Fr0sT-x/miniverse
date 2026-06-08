package dev.frost.miniverse.minigame.impl.bridge;

import net.minecraft.nbt.NbtCompound;

import java.util.Properties;

public record BridgeSettings(
    String mapId,
    int targetScore,
    int respawnDelaySeconds,
    int roundResetDelaySeconds,
    int voidDeathOffset,
    int heightLimitOffset,
    boolean allowBuilding,
    boolean allowBlockBreaking,
    boolean keepInventoryOnDeath,
    boolean enableBow,
    boolean enablePickaxe
) {
    public static BridgeSettings fromNbt(NbtCompound nbt) {
        if (nbt == null) {
            nbt = new NbtCompound();
        }
        return new BridgeSettings(
            nbt.contains("mapId") ? nbt.getString("mapId") : "",
            nbt.contains("targetScore") ? nbt.getInt("targetScore") : 5,
            nbt.contains("respawnDelaySeconds") ? nbt.getInt("respawnDelaySeconds") : 3,
            nbt.contains("roundResetDelaySeconds") ? nbt.getInt("roundResetDelaySeconds") : 5,
            nbt.contains("voidDeathOffset") ? nbt.getInt("voidDeathOffset") : 60,
            nbt.contains("heightLimitOffset") ? nbt.getInt("heightLimitOffset") : 0,
            !nbt.contains("allowBuilding") || nbt.getBoolean("allowBuilding"),
            !nbt.contains("allowBlockBreaking") || nbt.getBoolean("allowBlockBreaking"),
            !nbt.contains("keepInventoryOnDeath") || nbt.getBoolean("keepInventoryOnDeath"),
            !nbt.contains("enableBow") || nbt.getBoolean("enableBow"),
            !nbt.contains("enablePickaxe") || nbt.getBoolean("enablePickaxe")
        );
    }

    public void writeTo(Properties properties) {
        properties.setProperty("bridge.mapId", this.mapId);
        properties.setProperty("bridge.targetScore", String.valueOf(this.targetScore));
        properties.setProperty("bridge.respawnDelaySeconds", String.valueOf(this.respawnDelaySeconds));
        properties.setProperty("bridge.roundResetDelaySeconds", String.valueOf(this.roundResetDelaySeconds));
        properties.setProperty("bridge.voidDeathOffset", String.valueOf(this.voidDeathOffset));
        properties.setProperty("bridge.heightLimitOffset", String.valueOf(this.heightLimitOffset));
        properties.setProperty("bridge.allowBuilding", String.valueOf(this.allowBuilding));
        properties.setProperty("bridge.allowBlockBreaking", String.valueOf(this.allowBlockBreaking));
        properties.setProperty("bridge.keepInventoryOnDeath", String.valueOf(this.keepInventoryOnDeath));
        properties.setProperty("bridge.enableBow", String.valueOf(this.enableBow));
        properties.setProperty("bridge.enablePickaxe", String.valueOf(this.enablePickaxe));
    }

    public static BridgeSettings fromProperties(Properties properties) {
        return new BridgeSettings(
            properties.getProperty("bridge.mapId", ""),
            Integer.parseInt(properties.getProperty("bridge.targetScore", "5")),
            Integer.parseInt(properties.getProperty("bridge.respawnDelaySeconds", "3")),
            Integer.parseInt(properties.getProperty("bridge.roundResetDelaySeconds", "5")),
            Integer.parseInt(properties.getProperty("bridge.voidDeathOffset", "60")),
            Integer.parseInt(properties.getProperty("bridge.heightLimitOffset", "0")),
            Boolean.parseBoolean(properties.getProperty("bridge.allowBuilding", "true")),
            Boolean.parseBoolean(properties.getProperty("bridge.allowBlockBreaking", "true")),
            Boolean.parseBoolean(properties.getProperty("bridge.keepInventoryOnDeath", "true")),
            Boolean.parseBoolean(properties.getProperty("bridge.enableBow", "true")),
            Boolean.parseBoolean(properties.getProperty("bridge.enablePickaxe", "true"))
        );
    }
}
