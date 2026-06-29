package dev.frost.miniverse.minigame.impl.bedwars;

import net.minecraft.nbt.NbtCompound;

import java.util.Properties;

public record BedwarsSettings(
    String mapId,
    int respawnDelaySeconds,
    int ironGenIntervalTicks,
    int goldGenIntervalTicks,
    int diamondGenIntervalTicks,
    int emeraldGenIntervalTicks,
    int ironGenCap,
    int goldGenCap,
    int diamondGenCap,
    int emeraldGenCap
) {
    public static BedwarsSettings fromNbt(NbtCompound nbt) {
        if (nbt == null) {
            nbt = new NbtCompound();
        }
        return new BedwarsSettings(
            nbt.contains("mapId") ? nbt.getString("mapId") : "",
            nbt.contains("respawnDelaySeconds") ? nbt.getInt("respawnDelaySeconds") : 5,
            nbt.contains("ironGenIntervalTicks") ? nbt.getInt("ironGenIntervalTicks") : 20,
            nbt.contains("goldGenIntervalTicks") ? nbt.getInt("goldGenIntervalTicks") : 160,
            nbt.contains("diamondGenIntervalTicks") ? nbt.getInt("diamondGenIntervalTicks") : 500,
            nbt.contains("emeraldGenIntervalTicks") ? nbt.getInt("emeraldGenIntervalTicks") : 700,
            nbt.contains("ironGenCap") ? nbt.getInt("ironGenCap") : 64,
            nbt.contains("goldGenCap") ? nbt.getInt("goldGenCap") : 32,
            nbt.contains("diamondGenCap") ? nbt.getInt("diamondGenCap") : 8,
            nbt.contains("emeraldGenCap") ? nbt.getInt("emeraldGenCap") : 4
        );
    }

    public void writeTo(Properties properties) {
        properties.setProperty("bedwars.mapId", this.mapId);
        properties.setProperty("bedwars.respawnDelaySeconds", String.valueOf(this.respawnDelaySeconds));
        properties.setProperty("bedwars.ironGenIntervalTicks", String.valueOf(this.ironGenIntervalTicks));
        properties.setProperty("bedwars.goldGenIntervalTicks", String.valueOf(this.goldGenIntervalTicks));
        properties.setProperty("bedwars.diamondGenIntervalTicks", String.valueOf(this.diamondGenIntervalTicks));
        properties.setProperty("bedwars.emeraldGenIntervalTicks", String.valueOf(this.emeraldGenIntervalTicks));
        properties.setProperty("bedwars.ironGenCap", String.valueOf(this.ironGenCap));
        properties.setProperty("bedwars.goldGenCap", String.valueOf(this.goldGenCap));
        properties.setProperty("bedwars.diamondGenCap", String.valueOf(this.diamondGenCap));
        properties.setProperty("bedwars.emeraldGenCap", String.valueOf(this.emeraldGenCap));
    }

    public static BedwarsSettings fromProperties(Properties properties) {
        return new BedwarsSettings(
            properties.getProperty("bedwars.mapId", ""),
            Integer.parseInt(properties.getProperty("bedwars.respawnDelaySeconds", "5")),
            Integer.parseInt(properties.getProperty("bedwars.ironGenIntervalTicks", "20")),
            Integer.parseInt(properties.getProperty("bedwars.goldGenIntervalTicks", "160")),
            Integer.parseInt(properties.getProperty("bedwars.diamondGenIntervalTicks", "500")),
            Integer.parseInt(properties.getProperty("bedwars.emeraldGenIntervalTicks", "700")),
            Integer.parseInt(properties.getProperty("bedwars.ironGenCap", "64")),
            Integer.parseInt(properties.getProperty("bedwars.goldGenCap", "32")),
            Integer.parseInt(properties.getProperty("bedwars.diamondGenCap", "8")),
            Integer.parseInt(properties.getProperty("bedwars.emeraldGenCap", "4"))
        );
    }
}
