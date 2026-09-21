package dev.frost.miniverse.minigame.impl.zombies;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.NbtCompound;

public final class ZombiesDifficultyConfig {
    public record DifficultyEntry(
        float healthMultiplier,
        float damageMultiplier,
        double speedMultiplier,
        float windowBreakMultiplier,
        int baseWaveMobs,
        int waveMobsPerRound,
        int maxActiveMobs,
        int spawnCooldownMin,
        int spawnCooldownRandom,
        float specialAttackMultiplier
    ) {
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("healthMultiplier", healthMultiplier);
            obj.addProperty("damageMultiplier", damageMultiplier);
            obj.addProperty("speedMultiplier", speedMultiplier);
            obj.addProperty("windowBreakMultiplier", windowBreakMultiplier);
            obj.addProperty("baseWaveMobs", baseWaveMobs);
            obj.addProperty("waveMobsPerRound", waveMobsPerRound);
            obj.addProperty("maxActiveMobs", maxActiveMobs);
            obj.addProperty("spawnCooldownMin", spawnCooldownMin);
            obj.addProperty("spawnCooldownRandom", spawnCooldownRandom);
            obj.addProperty("specialAttackMultiplier", specialAttackMultiplier);
            return obj;
        }

        public static DifficultyEntry fromJson(JsonObject obj, DifficultyEntry fallback) {
            if (obj == null) return fallback;
            float hp = obj.has("healthMultiplier") ? obj.get("healthMultiplier").getAsFloat() : fallback.healthMultiplier;
            float dmg = obj.has("damageMultiplier") ? obj.get("damageMultiplier").getAsFloat() : fallback.damageMultiplier;
            double spd = obj.has("speedMultiplier") ? obj.get("speedMultiplier").getAsDouble() : fallback.speedMultiplier;
            float winBreak = obj.has("windowBreakMultiplier") ? obj.get("windowBreakMultiplier").getAsFloat() : fallback.windowBreakMultiplier;
            int baseMobs = obj.has("baseWaveMobs") ? obj.get("baseWaveMobs").getAsInt() : fallback.baseWaveMobs;
            int mobsPerRound = obj.has("waveMobsPerRound") ? obj.get("waveMobsPerRound").getAsInt() : fallback.waveMobsPerRound;
            int maxActive = obj.has("maxActiveMobs") ? obj.get("maxActiveMobs").getAsInt() : fallback.maxActiveMobs;
            int spawnMin = obj.has("spawnCooldownMin") ? obj.get("spawnCooldownMin").getAsInt() : fallback.spawnCooldownMin;
            int spawnRnd = obj.has("spawnCooldownRandom") ? obj.get("spawnCooldownRandom").getAsInt() : fallback.spawnCooldownRandom;
            float spec = obj.has("specialAttackMultiplier") ? obj.get("specialAttackMultiplier").getAsFloat() : fallback.specialAttackMultiplier;
            return new DifficultyEntry(hp, dmg, spd, winBreak, baseMobs, mobsPerRound, maxActive, spawnMin, spawnRnd, spec);
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putFloat("healthMultiplier", healthMultiplier);
            nbt.putFloat("damageMultiplier", damageMultiplier);
            nbt.putDouble("speedMultiplier", speedMultiplier);
            nbt.putFloat("windowBreakMultiplier", windowBreakMultiplier);
            nbt.putInt("baseWaveMobs", baseWaveMobs);
            nbt.putInt("waveMobsPerRound", waveMobsPerRound);
            nbt.putInt("maxActiveMobs", maxActiveMobs);
            nbt.putInt("spawnCooldownMin", spawnCooldownMin);
            nbt.putInt("spawnCooldownRandom", spawnCooldownRandom);
            nbt.putFloat("specialAttackMultiplier", specialAttackMultiplier);
            return nbt;
        }

        public static DifficultyEntry fromNbt(NbtCompound nbt, DifficultyEntry fallback) {
            if (nbt == null) return fallback;
            float hp = nbt.contains("healthMultiplier") ? nbt.getFloat("healthMultiplier") : fallback.healthMultiplier;
            float dmg = nbt.contains("damageMultiplier") ? nbt.getFloat("damageMultiplier") : fallback.damageMultiplier;
            double spd = nbt.contains("speedMultiplier") ? nbt.getDouble("speedMultiplier") : fallback.speedMultiplier;
            float winBreak = nbt.contains("windowBreakMultiplier") ? nbt.getFloat("windowBreakMultiplier") : fallback.windowBreakMultiplier;
            int baseMobs = nbt.contains("baseWaveMobs") ? nbt.getInt("baseWaveMobs") : fallback.baseWaveMobs;
            int mobsPerRound = nbt.contains("waveMobsPerRound") ? nbt.getInt("waveMobsPerRound") : fallback.waveMobsPerRound;
            int maxActive = nbt.contains("maxActiveMobs") ? nbt.getInt("maxActiveMobs") : fallback.maxActiveMobs;
            int spawnMin = nbt.contains("spawnCooldownMin") ? nbt.getInt("spawnCooldownMin") : fallback.spawnCooldownMin;
            int spawnRnd = nbt.contains("spawnCooldownRandom") ? nbt.getInt("spawnCooldownRandom") : fallback.spawnCooldownRandom;
            float spec = nbt.contains("specialAttackMultiplier") ? nbt.getFloat("specialAttackMultiplier") : fallback.specialAttackMultiplier;
            return new DifficultyEntry(hp, dmg, spd, winBreak, baseMobs, mobsPerRound, maxActive, spawnMin, spawnRnd, spec);
        }
    }

    private final Map<ZombiesDifficulty, DifficultyEntry> entries = new EnumMap<>(ZombiesDifficulty.class);

    public ZombiesDifficultyConfig() {
        this.resetToDefaults();
    }

    public ZombiesDifficultyConfig(Map<ZombiesDifficulty, DifficultyEntry> source) {
        this.resetToDefaults();
        if (source != null) {
            this.entries.putAll(source);
        }
    }

    public void resetToDefaults() {
        this.entries.clear();
        for (ZombiesDifficulty d : ZombiesDifficulty.values()) {
            this.entries.put(d, defaultFor(d));
        }
    }

    public void resetDifficulty(ZombiesDifficulty difficulty) {
        if (difficulty != null) {
            this.entries.put(difficulty, defaultFor(difficulty));
        }
    }

    public static DifficultyEntry defaultFor(ZombiesDifficulty d) {
        if (d == null) d = ZombiesDifficulty.EASY;
        return new DifficultyEntry(
            d.getHealthMultiplier(),
            d.getDamageMultiplier(),
            d.getSpeedMultiplier(),
            d.getWindowBreakMultiplier(),
            d.getBaseWaveMobs(),
            d.getWaveMobsPerRound(),
            d.getMaxActiveMobs(),
            d.getSpawnCooldownMin(),
            d.getSpawnCooldownRandom(),
            d.getSpecialAttackMultiplier()
        );
    }

    public static ZombiesDifficultyConfig defaults() {
        return new ZombiesDifficultyConfig();
    }

    public DifficultyEntry getEntry(ZombiesDifficulty difficulty) {
        if (difficulty == null) difficulty = ZombiesDifficulty.EASY;
        DifficultyEntry entry = this.entries.get(difficulty);
        if (entry == null) {
            entry = defaultFor(difficulty);
            this.entries.put(difficulty, entry);
        }
        return entry;
    }

    public void setEntry(ZombiesDifficulty difficulty, DifficultyEntry entry) {
        if (difficulty == null || entry == null) return;
        this.entries.put(difficulty, entry);
    }

    public float getHealthMultiplier(ZombiesDifficulty d) {
        return this.getEntry(d).healthMultiplier();
    }

    public float getDamageMultiplier(ZombiesDifficulty d) {
        return this.getEntry(d).damageMultiplier();
    }

    public double getSpeedMultiplier(ZombiesDifficulty d) {
        return this.getEntry(d).speedMultiplier();
    }

    public float getWindowBreakMultiplier(ZombiesDifficulty d) {
        return this.getEntry(d).windowBreakMultiplier();
    }

    public int getBaseWaveMobs(ZombiesDifficulty d) {
        return this.getEntry(d).baseWaveMobs();
    }

    public int getWaveMobsPerRound(ZombiesDifficulty d) {
        return this.getEntry(d).waveMobsPerRound();
    }

    public int getMaxActiveMobs(ZombiesDifficulty d) {
        return this.getEntry(d).maxActiveMobs();
    }

    public int getSpawnCooldownMin(ZombiesDifficulty d) {
        return this.getEntry(d).spawnCooldownMin();
    }

    public int getSpawnCooldownRandom(ZombiesDifficulty d) {
        return this.getEntry(d).spawnCooldownRandom();
    }

    public float getSpecialAttackMultiplier(ZombiesDifficulty d) {
        return this.getEntry(d).specialAttackMultiplier();
    }

    public Map<ZombiesDifficulty, DifficultyEntry> getEntries() {
        return Collections.unmodifiableMap(this.entries);
    }

    public String toJsonString() {
        JsonObject root = new JsonObject();
        for (Map.Entry<ZombiesDifficulty, DifficultyEntry> e : this.entries.entrySet()) {
            root.add(e.getKey().name(), e.getValue().toJson());
        }
        return root.toString();
    }

    public static ZombiesDifficultyConfig fromJsonString(String json) {
        ZombiesDifficultyConfig config = new ZombiesDifficultyConfig();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return config;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed.isJsonObject()) {
                JsonObject root = parsed.getAsJsonObject();
                for (Map.Entry<String, JsonElement> member : root.entrySet()) {
                    try {
                        ZombiesDifficulty d = ZombiesDifficulty.valueOf(member.getKey().toUpperCase());
                        if (member.getValue().isJsonObject()) {
                            DifficultyEntry fallback = config.getEntry(d);
                            config.entries.put(d, DifficultyEntry.fromJson(member.getValue().getAsJsonObject(), fallback));
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return config;
    }

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        for (Map.Entry<ZombiesDifficulty, DifficultyEntry> e : this.entries.entrySet()) {
            root.put(e.getKey().name(), e.getValue().toNbt());
        }
        return root;
    }

    public static ZombiesDifficultyConfig fromNbt(NbtCompound nbt) {
        ZombiesDifficultyConfig config = new ZombiesDifficultyConfig();
        if (nbt == null) return config;
        for (ZombiesDifficulty d : ZombiesDifficulty.values()) {
            if (nbt.contains(d.name())) {
                NbtCompound entryNbt = nbt.getCompound(d.name());
                DifficultyEntry fallback = config.getEntry(d);
                config.entries.put(d, DifficultyEntry.fromNbt(entryNbt, fallback));
            }
        }
        return config;
    }

    public void copyFrom(ZombiesDifficultyConfig other) {
        if (other == null) return;
        this.entries.clear();
        this.entries.putAll(other.entries);
    }
}
