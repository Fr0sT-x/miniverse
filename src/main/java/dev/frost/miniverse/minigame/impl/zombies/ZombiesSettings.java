package dev.frost.miniverse.minigame.impl.zombies;

import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponCustomConfig;
import net.minecraft.nbt.NbtCompound;

import java.util.Properties;

public record ZombiesSettings(
    String mapId,
    int startGold,
    int maxRounds,
    int intermissionSeconds,
    int bleedoutSeconds,
    boolean friendlyFire,
    ZombiesDifficulty difficulty,
    boolean endlessMode,
    WeaponCustomConfig weaponConfig,
    ZombiesDifficultyConfig difficultyConfig
) {
    public ZombiesSettings(String mapId, int startGold, int maxRounds, int intermissionSeconds, int bleedoutSeconds, boolean friendlyFire) {
        this(mapId, startGold, maxRounds, intermissionSeconds, bleedoutSeconds, friendlyFire, ZombiesDifficulty.EASY, false, WeaponCustomConfig.defaults(), ZombiesDifficultyConfig.defaults());
    }

    public ZombiesSettings(String mapId, int startGold, int maxRounds, int intermissionSeconds, int bleedoutSeconds, boolean friendlyFire, ZombiesDifficulty difficulty) {
        this(mapId, startGold, maxRounds, intermissionSeconds, bleedoutSeconds, friendlyFire, difficulty, false, WeaponCustomConfig.defaults(), ZombiesDifficultyConfig.defaults());
    }

    public ZombiesSettings(String mapId, int startGold, int maxRounds, int intermissionSeconds, int bleedoutSeconds, boolean friendlyFire, ZombiesDifficulty difficulty, boolean endlessMode) {
        this(mapId, startGold, maxRounds, intermissionSeconds, bleedoutSeconds, friendlyFire, difficulty, endlessMode, WeaponCustomConfig.defaults(), ZombiesDifficultyConfig.defaults());
    }

    public static ZombiesSettings defaults() {
        return new ZombiesSettings("dead_end", 500, 30, 10, 30, false, ZombiesDifficulty.EASY, false, WeaponCustomConfig.defaults(), ZombiesDifficultyConfig.defaults());
    }

    public static ZombiesSettings fromNbt(NbtCompound nbt) {
        if (nbt == null) {
            return defaults();
        }
        ZombiesDifficulty diff = ZombiesDifficulty.EASY;
        if (nbt.contains("difficulty")) {
            try {
                diff = ZombiesDifficulty.valueOf(nbt.getString("difficulty").toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        boolean endless = nbt.contains("endlessMode") && nbt.getBoolean("endlessMode");
        WeaponCustomConfig weapons = WeaponCustomConfig.defaults();
        if (nbt.contains("weaponConfig", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            weapons = WeaponCustomConfig.fromNbt(nbt.getCompound("weaponConfig"));
        } else if (nbt.contains("weaponConfig", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            weapons = WeaponCustomConfig.fromJsonString(nbt.getString("weaponConfig"));
        }

        ZombiesDifficultyConfig diffConfig = ZombiesDifficultyConfig.defaults();
        if (nbt.contains("difficultyConfig", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            diffConfig = ZombiesDifficultyConfig.fromNbt(nbt.getCompound("difficultyConfig"));
        } else if (nbt.contains("difficultyConfig", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            diffConfig = ZombiesDifficultyConfig.fromJsonString(nbt.getString("difficultyConfig"));
        }

        return new ZombiesSettings(
            nbt.contains("mapId") ? nbt.getString("mapId") : "dead_end",
            nbt.contains("startGold") ? nbt.getInt("startGold") : 500,
            nbt.contains("maxRounds") ? nbt.getInt("maxRounds") : 30,
            nbt.contains("intermissionSeconds") ? nbt.getInt("intermissionSeconds") : 10,
            nbt.contains("bleedoutSeconds") ? nbt.getInt("bleedoutSeconds") : 30,
            nbt.contains("friendlyFire") && nbt.getBoolean("friendlyFire"),
            diff,
            endless,
            weapons,
            diffConfig
        );
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("mapId", this.mapId);
        nbt.putInt("startGold", this.startGold);
        nbt.putInt("maxRounds", this.maxRounds);
        nbt.putInt("intermissionSeconds", this.intermissionSeconds);
        nbt.putInt("bleedoutSeconds", this.bleedoutSeconds);
        nbt.putBoolean("friendlyFire", this.friendlyFire);
        nbt.putString("difficulty", this.difficulty != null ? this.difficulty.name() : ZombiesDifficulty.EASY.name());
        nbt.putBoolean("endlessMode", this.endlessMode);
        if (this.weaponConfig != null) {
            nbt.put("weaponConfig", this.weaponConfig.toNbt());
        }
        if (this.difficultyConfig != null) {
            nbt.put("difficultyConfig", this.difficultyConfig.toNbt());
        }
        return nbt;
    }

    public void writeTo(Properties properties) {
        properties.setProperty("zombies.mapId", this.mapId);
        properties.setProperty("zombies.startGold", String.valueOf(this.startGold));
        properties.setProperty("zombies.maxRounds", String.valueOf(this.maxRounds));
        properties.setProperty("zombies.intermissionSeconds", String.valueOf(this.intermissionSeconds));
        properties.setProperty("zombies.bleedoutSeconds", String.valueOf(this.bleedoutSeconds));
        properties.setProperty("zombies.friendlyFire", String.valueOf(this.friendlyFire));
        properties.setProperty("zombies.difficulty", this.difficulty != null ? this.difficulty.name() : ZombiesDifficulty.EASY.name());
        properties.setProperty("zombies.endlessMode", String.valueOf(this.endlessMode));
        properties.setProperty("zombies.weaponConfig", this.weaponConfig != null ? this.weaponConfig.toJsonString() : "{}");
        properties.setProperty("zombies.difficultyConfig", this.difficultyConfig != null ? this.difficultyConfig.toJsonString() : "{}");
    }

    public static ZombiesSettings fromProperties(Properties properties) {
        if (properties == null) {
            return defaults();
        }
        ZombiesDifficulty diff = ZombiesDifficulty.EASY;
        String diffStr = properties.getProperty("zombies.difficulty");
        if (diffStr != null) {
            try {
                diff = ZombiesDifficulty.valueOf(diffStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        boolean endless = Boolean.parseBoolean(properties.getProperty("zombies.endlessMode", "false"));
        WeaponCustomConfig weapons = WeaponCustomConfig.fromJsonString(properties.getProperty("zombies.weaponConfig", "{}"));
        ZombiesDifficultyConfig diffConfig = ZombiesDifficultyConfig.fromJsonString(properties.getProperty("zombies.difficultyConfig", "{}"));
        return new ZombiesSettings(
            properties.getProperty("zombies.mapId", "dead_end"),
            Integer.parseInt(properties.getProperty("zombies.startGold", "500")),
            Integer.parseInt(properties.getProperty("zombies.maxRounds", "30")),
            Integer.parseInt(properties.getProperty("zombies.intermissionSeconds", "10")),
            Integer.parseInt(properties.getProperty("zombies.bleedoutSeconds", "30")),
            Boolean.parseBoolean(properties.getProperty("zombies.friendlyFire", "false")),
            diff,
            endless,
            weapons,
            diffConfig
        );
    }
}

