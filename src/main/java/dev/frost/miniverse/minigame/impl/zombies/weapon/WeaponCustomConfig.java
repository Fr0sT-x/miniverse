package dev.frost.miniverse.minigame.impl.zombies.weapon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtCompound;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WeaponCustomConfig {
    public record WeaponEntry(float damage, int reloadTicks, boolean inLuckyChest) {
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("damage", damage);
            obj.addProperty("reloadTicks", reloadTicks);
            obj.addProperty("inLuckyChest", inLuckyChest);
            return obj;
        }

        public static WeaponEntry fromJson(JsonObject obj, WeaponEntry fallback) {
            if (obj == null) return fallback;
            float dmg = obj.has("damage") ? obj.get("damage").getAsFloat() : fallback.damage;
            int reload = obj.has("reloadTicks") ? obj.get("reloadTicks").getAsInt() : fallback.reloadTicks;
            boolean chest = obj.has("inLuckyChest") ? obj.get("inLuckyChest").getAsBoolean() : fallback.inLuckyChest;
            return new WeaponEntry(dmg, reload, chest);
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putFloat("damage", damage);
            nbt.putInt("reloadTicks", reloadTicks);
            nbt.putBoolean("inLuckyChest", inLuckyChest);
            return nbt;
        }

        public static WeaponEntry fromNbt(NbtCompound nbt, WeaponEntry fallback) {
            if (nbt == null) return fallback;
            float dmg = nbt.contains("damage") ? nbt.getFloat("damage") : fallback.damage;
            int reload = nbt.contains("reloadTicks") ? nbt.getInt("reloadTicks") : fallback.reloadTicks;
            boolean chest = nbt.contains("inLuckyChest") ? nbt.getBoolean("inLuckyChest") : fallback.inLuckyChest;
            return new WeaponEntry(dmg, reload, chest);
        }
    }

    public static final Map<String, WeaponEntry> DEFAULTS = Map.ofEntries(
        Map.entry("KNIFE", new WeaponEntry(5.0f, 10, false)),
        Map.entry("PISTOL", new WeaponEntry(5.0f, 30, true)),
        Map.entry("SHOTGUN", new WeaponEntry(1.8f, 30, true)),
        Map.entry("RIFLE", new WeaponEntry(5.0f, 30, true)),
        Map.entry("SNIPER", new WeaponEntry(12.0f, 30, true)),
        Map.entry("FLAME_THROWER", new WeaponEntry(2.5f, 35, true)),
        Map.entry("GOLD_DIGGER", new WeaponEntry(7.0f, 25, true)),
        Map.entry("ROCKET_LAUNCHER", new WeaponEntry(7.0f, 40, true)),
        Map.entry("ZOMBIE_ZAPPER", new WeaponEntry(12.0f, 30, true)),
        Map.entry("LASER_GUN", new WeaponEntry(10.0f, 20, false)),
        Map.entry("DOUBLE_BARREL", new WeaponEntry(2.5f, 25, false)),
        Map.entry("ASSAULT_RIFLE", new WeaponEntry(9.0f, 25, false)),
        Map.entry("RAILGUN", new WeaponEntry(16.0f, 25, false)),
        Map.entry("NUKE_LAUNCHER", new WeaponEntry(11.0f, 35, false)),
        Map.entry("TESLA_GUN", new WeaponEntry(22.0f, 25, false))
    );

    private final Map<String, WeaponEntry> entries = new LinkedHashMap<>();

    public WeaponCustomConfig() {
        this.resetToDefaults();
    }

    public WeaponCustomConfig(Map<String, WeaponEntry> source) {
        this.resetToDefaults();
        if (source != null) {
            for (Map.Entry<String, WeaponEntry> e : source.entrySet()) {
                this.entries.put(e.getKey().toUpperCase(Locale.ROOT), e.getValue());
            }
        }
    }

    public void resetToDefaults() {
        this.entries.clear();
        this.entries.putAll(DEFAULTS);
    }

    public static WeaponCustomConfig defaults() {
        return new WeaponCustomConfig();
    }

    public WeaponEntry getEntry(String key) {
        if (key == null) return new WeaponEntry(5.0f, 30, false);
        String upper = key.toUpperCase(Locale.ROOT);
        WeaponEntry entry = this.entries.get(upper);
        if (entry == null) {
            entry = DEFAULTS.getOrDefault(upper, new WeaponEntry(5.0f, 30, false));
            this.entries.put(upper, entry);
        }
        return entry;
    }

    public WeaponEntry getEntry(WeaponType type) {
        return type != null ? getEntry(type.name()) : new WeaponEntry(5.0f, 30, false);
    }

    public void setEntry(String key, float damage, int reloadTicks, boolean inLuckyChest) {
        if (key == null) return;
        this.entries.put(key.toUpperCase(Locale.ROOT), new WeaponEntry(Math.max(0.1f, damage), Math.max(1, reloadTicks), inLuckyChest));
    }

    public void setEntry(WeaponType type, float damage, int reloadTicks, boolean inLuckyChest) {
        if (type != null) {
            setEntry(type.name(), damage, reloadTicks, inLuckyChest);
        }
    }

    public void setDamage(String key, float damage) {
        WeaponEntry current = getEntry(key);
        setEntry(key, damage, current.reloadTicks(), current.inLuckyChest());
    }

    public void setDamage(WeaponType type, float damage) {
        if (type != null) {
            setDamage(type.name(), damage);
        }
    }

    public void setReloadTicks(String key, int reloadTicks) {
        WeaponEntry current = getEntry(key);
        setEntry(key, current.damage(), reloadTicks, current.inLuckyChest());
    }

    public void setReloadTicks(WeaponType type, int reloadTicks) {
        if (type != null) {
            setReloadTicks(type.name(), reloadTicks);
        }
    }

    public void setInLuckyChest(String key, boolean inLuckyChest) {
        WeaponEntry current = getEntry(key);
        setEntry(key, current.damage(), current.reloadTicks(), inLuckyChest);
    }

    public void setInLuckyChest(WeaponType type, boolean inLuckyChest) {
        if (type != null) {
            setInLuckyChest(type.name(), inLuckyChest);
        }
    }

    public float getDamage(String key) {
        return getEntry(key).damage();
    }

    public float getDamage(WeaponType type) {
        return type != null ? getDamage(type.name()) : 5.0f;
    }

    public int getReloadTicks(String key) {
        return getEntry(key).reloadTicks();
    }

    public int getReloadTicks(WeaponType type) {
        return type != null ? getReloadTicks(type.name()) : 30;
    }

    public boolean isAllowedInLuckyChest(String key) {
        return getEntry(key).inLuckyChest();
    }

    public boolean isAllowedInLuckyChest(WeaponType type) {
        return type != null && isAllowedInLuckyChest(type.name());
    }

    public Map<String, WeaponEntry> getEntries() {
        return Collections.unmodifiableMap(this.entries);
    }

    public String toJsonString() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, WeaponEntry> e : this.entries.entrySet()) {
            root.add(e.getKey(), e.getValue().toJson());
        }
        return root.toString();
    }

    public static WeaponCustomConfig fromJsonString(String json) {
        WeaponCustomConfig config = new WeaponCustomConfig();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return config;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed.isJsonObject()) {
                JsonObject root = parsed.getAsJsonObject();
                for (Map.Entry<String, JsonElement> member : root.entrySet()) {
                    String key = member.getKey().toUpperCase(Locale.ROOT);
                    if (member.getValue().isJsonObject()) {
                        WeaponEntry fallback = config.getEntry(key);
                        config.entries.put(key, WeaponEntry.fromJson(member.getValue().getAsJsonObject(), fallback));
                    }
                }
            }
        } catch (Exception ignored) {}
        return config;
    }

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        for (Map.Entry<String, WeaponEntry> e : this.entries.entrySet()) {
            root.put(e.getKey(), e.getValue().toNbt());
        }
        return root;
    }

    public static WeaponCustomConfig fromNbt(NbtCompound nbt) {
        WeaponCustomConfig config = new WeaponCustomConfig();
        if (nbt == null) return config;
        for (String key : nbt.getKeys()) {
            if (nbt.contains(key, net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
                NbtCompound entryNbt = nbt.getCompound(key);
                WeaponEntry fallback = config.getEntry(key);
                config.entries.put(key.toUpperCase(Locale.ROOT), WeaponEntry.fromNbt(entryNbt, fallback));
            }
        }
        return config;
    }

    public void copyFrom(WeaponCustomConfig other) {
        if (other == null) return;
        this.entries.clear();
        this.entries.putAll(other.entries);
    }
}
