package dev.frost.miniverse.minigame.impl.zombies;

import net.minecraft.nbt.NbtCompound;

import java.util.Properties;

public record ZombiesSettings(
    String mapId,
    int startGold,
    int maxRounds,
    int intermissionSeconds,
    int bleedoutSeconds,
    boolean friendlyFire
) {
    public static ZombiesSettings defaults() {
        return new ZombiesSettings("dead_end", 500, 30, 10, 30, false);
    }

    public static ZombiesSettings fromNbt(NbtCompound nbt) {
        if (nbt == null) {
            return defaults();
        }
        return new ZombiesSettings(
            nbt.contains("mapId") ? nbt.getString("mapId") : "dead_end",
            nbt.contains("startGold") ? nbt.getInt("startGold") : 500,
            nbt.contains("maxRounds") ? nbt.getInt("maxRounds") : 30,
            nbt.contains("intermissionSeconds") ? nbt.getInt("intermissionSeconds") : 10,
            nbt.contains("bleedoutSeconds") ? nbt.getInt("bleedoutSeconds") : 30,
            nbt.contains("friendlyFire") && nbt.getBoolean("friendlyFire")
        );
    }

    public void writeTo(Properties properties) {
        properties.setProperty("zombies.mapId", this.mapId);
        properties.setProperty("zombies.startGold", String.valueOf(this.startGold));
        properties.setProperty("zombies.maxRounds", String.valueOf(this.maxRounds));
        properties.setProperty("zombies.intermissionSeconds", String.valueOf(this.intermissionSeconds));
        properties.setProperty("zombies.bleedoutSeconds", String.valueOf(this.bleedoutSeconds));
        properties.setProperty("zombies.friendlyFire", String.valueOf(this.friendlyFire));
    }

    public static ZombiesSettings fromProperties(Properties properties) {
        if (properties == null) {
            return defaults();
        }
        return new ZombiesSettings(
            properties.getProperty("zombies.mapId", "dead_end"),
            Integer.parseInt(properties.getProperty("zombies.startGold", "500")),
            Integer.parseInt(properties.getProperty("zombies.maxRounds", "30")),
            Integer.parseInt(properties.getProperty("zombies.intermissionSeconds", "10")),
            Integer.parseInt(properties.getProperty("zombies.bleedoutSeconds", "30")),
            Boolean.parseBoolean(properties.getProperty("zombies.friendlyFire", "false"))
        );
    }
}
