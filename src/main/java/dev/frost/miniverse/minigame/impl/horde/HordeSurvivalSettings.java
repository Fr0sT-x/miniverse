package dev.frost.miniverse.minigame.impl.horde;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public record HordeSurvivalSettings(
    int totalWaves,
    int uplinkDurationSeconds,
    int intermissionSeconds,
    int initialPodFuel,
    int harvestRadius,
    int fuelDrainPerSecond,
    int borderSize,
    boolean emergencyFlaresEnabled,
    List<TeamMember> survivors
) {
    public static final int DEFAULT_TOTAL_WAVES = 15;
    public static final int DEFAULT_UPLINK_DURATION_SECONDS = 90;
    public static final int DEFAULT_INTERMISSION_SECONDS = 45;
    public static final int DEFAULT_INITIAL_POD_FUEL = 50;
    public static final int DEFAULT_HARVEST_RADIUS = 20;
    public static final int DEFAULT_FUEL_DRAIN_PER_SECOND = 1;
    public static final int DEFAULT_BORDER_SIZE = 1000;

    public HordeSurvivalSettings {
        survivors = List.copyOf(normalizeMembers(survivors));
    }

    public static HordeSurvivalSettings defaults() {
        return new HordeSurvivalSettings(
            DEFAULT_TOTAL_WAVES,
            DEFAULT_UPLINK_DURATION_SECONDS,
            DEFAULT_INTERMISSION_SECONDS,
            DEFAULT_INITIAL_POD_FUEL,
            DEFAULT_HARVEST_RADIUS,
            DEFAULT_FUEL_DRAIN_PER_SECOND,
            DEFAULT_BORDER_SIZE,
            true,
            List.of()
        );
    }

    public static HordeSurvivalSettings fromNbt(NbtCompound nbt) {
        HordeSurvivalSettings defaults = defaults();
        if (nbt == null || nbt.isEmpty()) {
            return defaults;
        }

        return new HordeSurvivalSettings(
            clamp(getIntOrDefault(nbt, "totalWaves", defaults.totalWaves()), 1, 100),
            clamp(getIntOrDefault(nbt, "uplinkDurationSeconds", defaults.uplinkDurationSeconds()), 15, 600),
            clamp(getIntOrDefault(nbt, "intermissionSeconds", defaults.intermissionSeconds()), 5, 600),
            clamp(getIntOrDefault(nbt, "initialPodFuel", defaults.initialPodFuel()), 10, 100),
            clamp(getIntOrDefault(nbt, "harvestRadius", defaults.harvestRadius()), 5, 25),
            clamp(getIntOrDefault(nbt, "fuelDrainPerSecond", defaults.fuelDrainPerSecond()), 1, 10),
            clamp(getIntOrDefault(nbt, "borderSize", defaults.borderSize()), 50, 10000),
            getBooleanOrDefault(nbt, "emergencyFlaresEnabled", defaults.emergencyFlaresEnabled()),
            readMembers(getListOrEmpty(nbt, "survivors", NbtElement.COMPOUND_TYPE))
        );
    }

    public static HordeSurvivalSettings fromProperties(Properties properties) {
        HordeSurvivalSettings defaults = defaults();
        if (properties == null || properties.isEmpty()) {
            return defaults;
        }

        return new HordeSurvivalSettings(
            clamp(parseInt(properties.getProperty("horde.totalWaves"), defaults.totalWaves()), 1, 100),
            clamp(parseInt(properties.getProperty("horde.uplinkDurationSeconds"), defaults.uplinkDurationSeconds()), 15, 600),
            clamp(parseInt(properties.getProperty("horde.intermissionSeconds"), defaults.intermissionSeconds()), 5, 600),
            clamp(parseInt(properties.getProperty("horde.initialPodFuel"), defaults.initialPodFuel()), 10, 100),
            clamp(parseInt(properties.getProperty("horde.harvestRadius"), defaults.harvestRadius()), 5, 25),
            clamp(parseInt(properties.getProperty("horde.fuelDrainPerSecond"), defaults.fuelDrainPerSecond()), 1, 10),
            clamp(parseInt(properties.getProperty("horde.borderSize"), defaults.borderSize()), 50, 10000),
            parseBoolean(properties.getProperty("horde.emergencyFlaresEnabled"), defaults.emergencyFlaresEnabled()),
            readMembers(properties)
        );
    }

    public void writeTo(Properties properties) {
        properties.setProperty("horde.totalWaves", Integer.toString(this.totalWaves));
        properties.setProperty("horde.uplinkDurationSeconds", Integer.toString(this.uplinkDurationSeconds));
        properties.setProperty("horde.intermissionSeconds", Integer.toString(this.intermissionSeconds));
        properties.setProperty("horde.initialPodFuel", Integer.toString(this.initialPodFuel));
        properties.setProperty("horde.harvestRadius", Integer.toString(this.harvestRadius));
        properties.setProperty("horde.fuelDrainPerSecond", Integer.toString(this.fuelDrainPerSecond));
        properties.setProperty("horde.borderSize", Integer.toString(this.borderSize));
        properties.setProperty("horde.emergencyFlaresEnabled", Boolean.toString(this.emergencyFlaresEnabled));
        properties.setProperty("horde.survivors.count", Integer.toString(this.survivors.size()));

        for (int i = 0; i < this.survivors.size(); i++) {
            TeamMember member = this.survivors.get(i);
            properties.setProperty("horde.survivor." + i + ".uuid", member.uuid().toString());
            properties.setProperty("horde.survivor." + i + ".name", member.name());
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("totalWaves", this.totalWaves);
        nbt.putInt("uplinkDurationSeconds", this.uplinkDurationSeconds);
        nbt.putInt("intermissionSeconds", this.intermissionSeconds);
        nbt.putInt("initialPodFuel", this.initialPodFuel);
        nbt.putInt("harvestRadius", this.harvestRadius);
        nbt.putInt("fuelDrainPerSecond", this.fuelDrainPerSecond);
        nbt.putInt("borderSize", this.borderSize);
        nbt.putBoolean("emergencyFlaresEnabled", this.emergencyFlaresEnabled);

        NbtList survivorsList = new NbtList();
        for (TeamMember member : this.survivors) {
            NbtCompound memberCompound = new NbtCompound();
            memberCompound.putString("uuid", member.uuid().toString());
            memberCompound.putString("name", member.name());
            survivorsList.add(memberCompound);
        }
        nbt.put("survivors", survivorsList);
        return nbt;
    }

    private static List<TeamMember> normalizeMembers(List<TeamMember> input) {
        List<TeamMember> list = new ArrayList<>();
        if (input == null) {
            return list;
        }
        LinkedHashMap<UUID, TeamMember> unique = new LinkedHashMap<>();
        for (TeamMember m : input) {
            if (m != null && m.uuid() != null) {
                String name = m.name() == null || m.name().isBlank() ? m.uuid().toString() : m.name().trim();
                unique.put(m.uuid(), new TeamMember(m.uuid(), name));
            }
        }
        list.addAll(unique.values());
        return list;
    }

    private static List<TeamMember> readMembers(NbtList list) {
        List<TeamMember> members = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound comp = list.getCompound(i);
            String uuid = comp.getString("uuid").trim();
            if (uuid.isBlank()) continue;
            try {
                UUID id = UUID.fromString(uuid);
                String name = comp.getString("name").trim();
                members.add(new TeamMember(id, name.isBlank() ? id.toString() : name));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return members;
    }

    private static List<TeamMember> readMembers(Properties properties) {
        int count = parseInt(properties.getProperty("horde.survivors.count"), 0);
        List<TeamMember> members = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String uuidStr = properties.getProperty("horde.survivor." + i + ".uuid", "").trim();
            if (uuidStr.isBlank()) continue;
            try {
                UUID id = UUID.fromString(uuidStr);
                String name = properties.getProperty("horde.survivor." + i + ".name", id.toString()).trim();
                members.add(new TeamMember(id, name));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return members;
    }

    private static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    private static int getIntOrDefault(NbtCompound nbt, String key, int fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE) ? nbt.getInt(key) : fallback;
    }

    private static boolean getBooleanOrDefault(NbtCompound nbt, String key, boolean fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE) ? nbt.getBoolean(key) : fallback;
    }

    private static NbtList getListOrEmpty(NbtCompound nbt, String key, int listType) {
        return nbt != null && nbt.contains(key, NbtElement.LIST_TYPE) ? nbt.getList(key, listType) : new NbtList();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
    }

    public record TeamMember(UUID uuid, String name) {}
}
