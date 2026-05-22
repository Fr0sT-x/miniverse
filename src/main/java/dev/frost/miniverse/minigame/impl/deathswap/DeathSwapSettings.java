package dev.frost.miniverse.minigame.impl.deathswap;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import dev.frost.miniverse.minigame.core.respawn.RespawnMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public record DeathSwapSettings(
    int swapIntervalSeconds,
    int initialGracePeriodSeconds,
    int borderSize,
    SeedMode seedMode,
    long seed,
    boolean keepInventory,
    boolean pvpEnabled,
    RespawnMode respawnMode,
    int pointsToWin,
    boolean preserveVelocity,
    List<TeamConfig> teams
) {
    private static final int DEFAULT_SWAP_INTERVAL_SECONDS = 300;
    private static final int DEFAULT_INITIAL_GRACE_PERIOD_SECONDS = 30;
    private static final int DEFAULT_BORDER_SIZE = 3000;
    private static final int DEFAULT_POINTS_TO_WIN = 5;

    public DeathSwapSettings {
        teams = List.copyOf(normalizeTeams(teams));
    }

    public static DeathSwapSettings defaults() {
        return new DeathSwapSettings(
            DEFAULT_SWAP_INTERVAL_SECONDS,
            DEFAULT_INITIAL_GRACE_PERIOD_SECONDS,
            DEFAULT_BORDER_SIZE,
            SeedMode.RANDOM,
            ThreadLocalRandom.current().nextLong(),
            true,
            false,
            RespawnMode.POINTS,
            DEFAULT_POINTS_TO_WIN,
            true,
            List.of()
        );
    }

    public static DeathSwapSettings fromNbt(NbtCompound nbt) {
        DeathSwapSettings defaults = defaults();
        if (nbt == null || nbt.isEmpty()) {
            return defaults;
        }

        return new DeathSwapSettings(
            clamp(getIntOrDefault(nbt, "swapIntervalSeconds", defaults.swapIntervalSeconds()), 1, 3600),
            clamp(getIntOrDefault(nbt, "initialGracePeriodSeconds", defaults.initialGracePeriodSeconds()), 0, 3600),
            clamp(getIntOrDefault(nbt, "borderSize", defaults.borderSize()), 16, 60_000),
            parseSeedMode(getStringOrDefault(nbt, "seedMode", defaults.seedMode().nbtValue())),
            getLongOrDefault(nbt, "seed", defaults.seed()),
            getBooleanOrDefault(nbt, "keepInventory", defaults.keepInventory()),
            getBooleanOrDefault(nbt, "pvpEnabled", defaults.pvpEnabled()),
            RespawnMode.parse(getStringOrDefault(nbt, "respawnMode", defaults.respawnMode().configValue()), defaults.respawnMode()),
            clamp(getIntOrDefault(nbt, "pointsToWin", defaults.pointsToWin()), 1, 100),
            getBooleanOrDefault(nbt, "preserveVelocity", defaults.preserveVelocity()),
            readTeams(getListOrEmpty(nbt, "teams", NbtElement.COMPOUND_TYPE))
        );
    }

    public static DeathSwapSettings fromProperties(Properties properties) {
        DeathSwapSettings defaults = defaults();
        if (properties == null || properties.isEmpty()) {
            return defaults;
        }

        return new DeathSwapSettings(
            clamp(parseInt(properties.getProperty("deathswap.swapIntervalSeconds"), defaults.swapIntervalSeconds()), 1, 3600),
            clamp(parseInt(properties.getProperty("deathswap.initialGracePeriodSeconds"), defaults.initialGracePeriodSeconds()), 0, 3600),
            clamp(parseInt(properties.getProperty("deathswap.borderSize"), defaults.borderSize()), 16, 60_000),
            parseSeedMode(properties.getProperty("deathswap.seedMode", defaults.seedMode().nbtValue())),
            parseLong(properties.getProperty("deathswap.seed"), defaults.seed()),
            parseBoolean(properties.getProperty("deathswap.keepInventory"), defaults.keepInventory()),
            parseBoolean(properties.getProperty("deathswap.pvpEnabled"), defaults.pvpEnabled()),
            RespawnMode.parse(properties.getProperty("deathswap.respawnMode", defaults.respawnMode().configValue()), defaults.respawnMode()),
            clamp(parseInt(properties.getProperty("deathswap.pointsToWin"), defaults.pointsToWin()), 1, 100),
            parseBoolean(properties.getProperty("deathswap.preserveVelocity"), defaults.preserveVelocity()),
            readTeams(properties)
        );
    }

    public void writeTo(Properties properties) {
        properties.setProperty("deathswap.swapIntervalSeconds", Integer.toString(this.swapIntervalSeconds));
        properties.setProperty("deathswap.initialGracePeriodSeconds", Integer.toString(this.initialGracePeriodSeconds));
        properties.setProperty("deathswap.borderSize", Integer.toString(this.borderSize));
        properties.setProperty("deathswap.seedMode", this.seedMode.nbtValue());
        properties.setProperty("deathswap.seed", Long.toString(this.seed));
        properties.setProperty("deathswap.keepInventory", Boolean.toString(this.keepInventory));
        properties.setProperty("deathswap.pvpEnabled", Boolean.toString(this.pvpEnabled));
        properties.setProperty("deathswap.respawnMode", this.respawnMode.configValue());
        properties.setProperty("deathswap.pointsToWin", Integer.toString(this.pointsToWin));
        properties.setProperty("deathswap.preserveVelocity", Boolean.toString(this.preserveVelocity));
        properties.setProperty("deathswap.teams.count", Integer.toString(this.teams.size()));

        for (int i = 0; i < this.teams.size(); i++) {
            TeamConfig team = this.teams.get(i);
            properties.setProperty("deathswap.team." + i + ".label", team.label());
            properties.setProperty("deathswap.team." + i + ".memberCount", Integer.toString(team.members().size()));
            for (int m = 0; m < team.members().size(); m++) {
                TeamMember member = team.members().get(m);
                properties.setProperty("deathswap.team." + i + ".member." + m + ".uuid", member.uuid().toString());
                properties.setProperty("deathswap.team." + i + ".member." + m + ".name", member.name());
            }
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("swapIntervalSeconds", this.swapIntervalSeconds);
        nbt.putInt("initialGracePeriodSeconds", this.initialGracePeriodSeconds);
        nbt.putInt("borderSize", this.borderSize);
        nbt.putString("seedMode", this.seedMode.nbtValue());
        nbt.putLong("seed", this.seed);
        nbt.putBoolean("keepInventory", this.keepInventory);
        nbt.putBoolean("pvpEnabled", this.pvpEnabled);
        nbt.putString("respawnMode", this.respawnMode.configValue());
        nbt.putInt("pointsToWin", this.pointsToWin);
        nbt.putBoolean("preserveVelocity", this.preserveVelocity);

        NbtList teamsList = new NbtList();
        for (TeamConfig team : this.teams) {
            NbtCompound teamCompound = new NbtCompound();
            teamCompound.putString("label", team.label());
            NbtList membersList = new NbtList();
            for (TeamMember member : team.members()) {
                NbtCompound memberCompound = new NbtCompound();
                memberCompound.putString("uuid", member.uuid().toString());
                memberCompound.putString("name", member.name());
                membersList.add(memberCompound);
            }
            teamCompound.put("members", membersList);
            teamsList.add(teamCompound);
        }
        nbt.put("teams", teamsList);
        return nbt;
    }

    public boolean hasExplicitTeams() {
        return !this.teams.isEmpty();
    }

    private static List<TeamConfig> normalizeTeams(List<TeamConfig> input) {
        List<TeamConfig> teams = new ArrayList<>();
        if (input == null) {
            return teams;
        }

        int index = 1;
        for (TeamConfig team : input) {
            if (team == null) {
                continue;
            }

            List<TeamMember> members = new ArrayList<>();
            if (team.members() != null) {
                LinkedHashMap<UUID, TeamMember> uniqueMembers = new LinkedHashMap<>();
                for (TeamMember member : team.members()) {
                    if (member == null || member.uuid() == null) {
                        continue;
                    }
                    String name = member.name() == null ? "" : member.name().trim();
                    if (name.isBlank()) {
                        name = member.uuid().toString();
                    }
                    uniqueMembers.put(member.uuid(), new TeamMember(member.uuid(), name));
                }
                members.addAll(uniqueMembers.values());
            }

            String label = team.label() == null ? "" : team.label().trim();
            if (label.isBlank()) {
                label = "Team " + index;
            }
            if (!members.isEmpty()) {
                teams.add(new TeamConfig(label, members));
                index++;
            }
        }
        return teams;
    }

    private static List<TeamConfig> readTeams(NbtList list) {
        List<TeamConfig> teams = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound teamCompound = list.getCompound(i);
            String label = getStringOrDefault(teamCompound, "label", "Team " + (i + 1)).trim();
            NbtList membersList = getListOrEmpty(teamCompound, "members", NbtElement.COMPOUND_TYPE);
            List<TeamMember> members = new ArrayList<>();
            for (int m = 0; m < membersList.size(); m++) {
                NbtCompound memberCompound = membersList.getCompound(m);
                String uuid = getStringOrDefault(memberCompound, "uuid", "").trim();
                if (uuid.isBlank()) {
                    continue;
                }
                try {
                    UUID memberUuid = UUID.fromString(uuid);
                    String name = getStringOrDefault(memberCompound, "name", memberUuid.toString()).trim();
                    if (name.isBlank()) {
                        name = memberUuid.toString();
                    }
                    members.add(new TeamMember(memberUuid, name));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (!members.isEmpty()) {
                teams.add(new TeamConfig(label, members));
            }
        }
        return teams;
    }

    private static List<TeamConfig> readTeams(Properties properties) {
        int count = parseInt(properties.getProperty("deathswap.teams.count"), -1);
        List<TeamConfig> teams = new ArrayList<>();

        if (count >= 0) {
            for (int i = 0; i < count; i++) {
                String label = properties.getProperty("deathswap.team." + i + ".label", "Team " + (i + 1)).trim();
                int memberCount = parseInt(properties.getProperty("deathswap.team." + i + ".memberCount"), 0);
                List<TeamMember> members = new ArrayList<>();
                for (int m = 0; m < memberCount; m++) {
                    String uuidValue = properties.getProperty("deathswap.team." + i + ".member." + m + ".uuid", "").trim();
                    if (uuidValue.isBlank()) {
                        continue;
                    }
                    try {
                        UUID memberUuid = UUID.fromString(uuidValue);
                        String name = properties.getProperty("deathswap.team." + i + ".member." + m + ".name", memberUuid.toString()).trim();
                        if (name.isBlank()) {
                            name = memberUuid.toString();
                        }
                        members.add(new TeamMember(memberUuid, name));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (!members.isEmpty()) {
                    teams.add(new TeamConfig(label, members));
                }
            }
        }

        return teams;
    }

    private static SeedMode parseSeedMode(String value) {
        try {
            return value == null || value.isBlank() ? SeedMode.RANDOM : SeedMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SeedMode.RANDOM;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    private static int getIntOrDefault(NbtCompound nbt, String key, int fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE)
            ? nbt.getInt(key)
            : fallback;
    }

    private static long getLongOrDefault(NbtCompound nbt, String key, long fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE)
            ? nbt.getLong(key)
            : fallback;
    }

    private static boolean getBooleanOrDefault(NbtCompound nbt, String key, boolean fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE)
            ? nbt.getBoolean(key)
            : fallback;
    }

    private static String getStringOrDefault(NbtCompound nbt, String key, String fallback) {
        return nbt != null && nbt.contains(key, NbtElement.STRING_TYPE)
            ? nbt.getString(key)
            : fallback;
    }

    private static NbtList getListOrEmpty(NbtCompound nbt, String key, int listType) {
        return nbt != null && nbt.contains(key, NbtElement.LIST_TYPE)
            ? nbt.getList(key, listType)
            : new NbtList();
    }

    public enum SeedMode {
        RANDOM,
        FIXED;

        public SeedMode next() {
            return this == RANDOM ? FIXED : RANDOM;
        }

        public String nbtValue() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record TeamConfig(String label, List<TeamMember> members) {
    }

    public record TeamMember(UUID uuid, String name) {
    }
}

