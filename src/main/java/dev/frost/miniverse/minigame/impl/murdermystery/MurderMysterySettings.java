package dev.frost.miniverse.minigame.impl.murdermystery;

import net.minecraft.nbt.NbtCompound;

public record MurderMysterySettings(
    int roundDurationTicks,
    int detectiveCount,
    int coinSpawnIntervalTicks,
    int detectiveBowPrice,
    String mapId
) {
    public static MurderMysterySettings defaults() {
        return new MurderMysterySettings(12000, 1, 100, 10, "");
    }

    public static MurderMysterySettings fromNbt(NbtCompound nbt) {
        if (nbt == null || nbt.isEmpty()) {
            return defaults();
        }
        return new MurderMysterySettings(
            nbt.contains("roundDurationTicks") ? nbt.getInt("roundDurationTicks") : 12000,
            nbt.contains("detectiveCount") ? nbt.getInt("detectiveCount") : 1,
            nbt.contains("coinSpawnIntervalTicks") ? nbt.getInt("coinSpawnIntervalTicks") : 100,
            nbt.contains("detectiveBowPrice") ? nbt.getInt("detectiveBowPrice") : 10,
            nbt.contains("mapId") ? nbt.getString("mapId") : ""
        );
    }

    public static MurderMysterySettings fromProperties(java.util.Properties properties) {
        if (properties == null || properties.isEmpty()) return defaults();
        return new MurderMysterySettings(
            Integer.parseInt(properties.getProperty("murdermystery.roundDurationTicks", "12000")),
            Integer.parseInt(properties.getProperty("murdermystery.detectiveCount", "1")),
            Integer.parseInt(properties.getProperty("murdermystery.coinSpawnIntervalTicks", "100")),
            Integer.parseInt(properties.getProperty("murdermystery.detectiveBowPrice", "10")),
            properties.getProperty("miniverse.murdermystery.mapId", "")
        );
    }

    public void writeTo(java.util.Properties properties) {
        properties.setProperty("murdermystery.roundDurationTicks", String.valueOf(this.roundDurationTicks));
        properties.setProperty("murdermystery.detectiveCount", String.valueOf(this.detectiveCount));
        properties.setProperty("murdermystery.coinSpawnIntervalTicks", String.valueOf(this.coinSpawnIntervalTicks));
        properties.setProperty("murdermystery.detectiveBowPrice", String.valueOf(this.detectiveBowPrice));
        if (this.mapId != null && !this.mapId.isBlank()) {
            properties.setProperty("miniverse.murdermystery.mapId", this.mapId);
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("roundDurationTicks", this.roundDurationTicks);
        nbt.putInt("detectiveCount", this.detectiveCount);
        nbt.putInt("coinSpawnIntervalTicks", this.coinSpawnIntervalTicks);
        nbt.putInt("detectiveBowPrice", this.detectiveBowPrice);
        nbt.putString("mapId", this.mapId);
        return nbt;
    }
}
