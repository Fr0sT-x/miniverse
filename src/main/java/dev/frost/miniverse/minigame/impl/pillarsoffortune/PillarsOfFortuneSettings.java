package dev.frost.miniverse.minigame.impl.pillarsoffortune;

import net.minecraft.nbt.NbtCompound;
import java.util.Properties;

public record PillarsOfFortuneSettings(
    int timeLimitSeconds,
    int lootDropIntervalSeconds,
    String activeModifier
) {
    public static PillarsOfFortuneSettings defaults() {
        return new PillarsOfFortuneSettings(600, 15, "none");
    }

    public static PillarsOfFortuneSettings fromNbt(NbtCompound nbt) {
        int timeLimit = nbt.contains("timeLimitSeconds") ? nbt.getInt("timeLimitSeconds") : 600;
        int lootDrop = nbt.contains("lootDropIntervalSeconds") ? nbt.getInt("lootDropIntervalSeconds") : 15;
        String mod = nbt.contains("activeModifier") ? nbt.getString("activeModifier") : "none";
        return new PillarsOfFortuneSettings(timeLimit, lootDrop, mod);
    }

    public static PillarsOfFortuneSettings fromProperties(Properties properties) {
        int timeLimit = Integer.parseInt(properties.getProperty("pillarsoffortune.timeLimitSeconds", "600"));
        int lootDrop = Integer.parseInt(properties.getProperty("pillarsoffortune.lootDropIntervalSeconds", "15"));
        String mod = properties.getProperty("pillarsoffortune.activeModifier", "none");
        return new PillarsOfFortuneSettings(timeLimit, lootDrop, mod);
    }

    public void writeTo(Properties properties) {
        properties.setProperty("pillarsoffortune.timeLimitSeconds", String.valueOf(timeLimitSeconds));
        properties.setProperty("pillarsoffortune.lootDropIntervalSeconds", String.valueOf(lootDropIntervalSeconds));
        properties.setProperty("pillarsoffortune.activeModifier", activeModifier);
    }
}
