package dev.frost.miniverse.minigame.impl.microparty;

import net.minecraft.nbt.NbtCompound;

import java.util.*;

public record MicroPartySettings(
    String mapId,
    int startingLives,
    int maxRounds,
    String gameMode,
    boolean speedScaling,
    int intermissionSeconds,
    Set<String> enabledRules,
    float speedMultiplier,
    Map<String, Integer> ruleDurations
) {
    public MicroPartySettings(String mapId, int startingLives, int maxRounds, String gameMode, boolean speedScaling, int intermissionSeconds) {
        this(mapId, startingLives, maxRounds, gameMode, speedScaling, intermissionSeconds, Set.of(), 2.5f, Map.of());
    }

    public MicroPartySettings(String mapId, int startingLives, int maxRounds, String gameMode, boolean speedScaling, int intermissionSeconds, Set<String> enabledRules) {
        this(mapId, startingLives, maxRounds, gameMode, speedScaling, intermissionSeconds, enabledRules, 2.5f, Map.of());
    }

    public static MicroPartySettings defaults() {
        return new MicroPartySettings("", 3, 25, "POINTS", true, 1, Set.of(), 2.5f, Map.of());
    }

    public boolean isRuleEnabled(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return false;
        }
        return this.enabledRules == null || this.enabledRules.isEmpty() || this.enabledRules.contains(ruleId);
    }

    public int getRuleDuration(String ruleId, int defaultSeconds) {
        if (this.ruleDurations == null || ruleId == null) {
            return defaultSeconds;
        }
        Integer custom = this.ruleDurations.get(ruleId);
        return custom != null && custom >= 1 ? custom : defaultSeconds;
    }

    public static MicroPartySettings fromNbt(NbtCompound nbt) {
        if (nbt == null) {
            return defaults();
        }

        Set<String> rules = new LinkedHashSet<>();
        if (nbt.contains("enabledRules")) {
            String raw = nbt.getString("enabledRules");
            if (!raw.isBlank()) {
                for (String part : raw.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        rules.add(trimmed);
                    }
                }
            }
        }

        float speedMult = nbt.contains("speedMultiplier") ? nbt.getFloat("speedMultiplier") : 2.5f;
        speedMult = Math.max(1.0f, Math.min(5.0f, speedMult));

        Map<String, Integer> durations = nbt.contains("ruleDurations")
            ? deserializeRuleDurations(nbt.getString("ruleDurations"))
            : Map.of();

        return new MicroPartySettings(
            nbt.contains("mapId") ? nbt.getString("mapId") : "",
            nbt.contains("startingLives") ? nbt.getInt("startingLives") : 3,
            nbt.contains("maxRounds") ? nbt.getInt("maxRounds") : 25,
            nbt.contains("gameMode") ? nbt.getString("gameMode") : "POINTS",
            !nbt.contains("speedScaling") || nbt.getBoolean("speedScaling"),
            nbt.contains("intermissionSeconds") ? nbt.getInt("intermissionSeconds") : 1,
            Set.copyOf(rules),
            speedMult,
            durations
        );
    }

    public void writeTo(Properties properties) {
        properties.setProperty("microparty.mapId", this.mapId != null ? this.mapId : "");
        properties.setProperty("microparty.startingLives", String.valueOf(this.startingLives));
        properties.setProperty("microparty.maxRounds", String.valueOf(this.maxRounds));
        properties.setProperty("microparty.gameMode", this.gameMode != null ? this.gameMode : "POINTS");
        properties.setProperty("microparty.speedScaling", String.valueOf(this.speedScaling));
        properties.setProperty("microparty.intermissionSeconds", String.valueOf(this.intermissionSeconds));
        properties.setProperty("microparty.enabledRules", this.enabledRules != null ? String.join(",", this.enabledRules) : "");
        properties.setProperty("microparty.speedMultiplier", String.format(Locale.ROOT, "%.2f", this.speedMultiplier));
        properties.setProperty("microparty.ruleDurations", serializeRuleDurations(this.ruleDurations));
    }

    public static MicroPartySettings fromProperties(Properties properties) {
        if (properties == null) {
            return defaults();
        }

        Set<String> rules = new LinkedHashSet<>();
        String rawRules = properties.getProperty("microparty.enabledRules", "");
        if (!rawRules.isBlank()) {
            for (String part : rawRules.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    rules.add(trimmed);
                }
            }
        }

        float speedMult = parseFloat(properties.getProperty("microparty.speedMultiplier"), 2.5f);
        speedMult = Math.max(1.0f, Math.min(5.0f, speedMult));

        Map<String, Integer> durations = deserializeRuleDurations(properties.getProperty("microparty.ruleDurations", ""));

        return new MicroPartySettings(
            properties.getProperty("microparty.mapId", ""),
            parseInt(properties.getProperty("microparty.startingLives"), 3),
            parseInt(properties.getProperty("microparty.maxRounds"), 25),
            properties.getProperty("microparty.gameMode", "POINTS"),
            Boolean.parseBoolean(properties.getProperty("microparty.speedScaling", "true")),
            parseInt(properties.getProperty("microparty.intermissionSeconds"), 1),
            Set.copyOf(rules),
            speedMult,
            durations
        );
    }

    public static String serializeRuleDurations(Map<String, Integer> durations) {
        if (durations == null || durations.isEmpty()) {
            return "";
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> e : durations.entrySet()) {
            if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null && e.getValue() >= 1) {
                entries.add(e.getKey().trim() + ":" + e.getValue());
            }
        }
        return String.join(",", entries);
    }

    public static Map<String, Integer> deserializeRuleDurations(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            int colon = trimmed.indexOf(':');
            if (colon > 0 && colon + 1 < trimmed.length()) {
                String ruleId = trimmed.substring(0, colon).trim();
                String secStr = trimmed.substring(colon + 1).trim();
                try {
                    int sec = Integer.parseInt(secStr);
                    if (sec >= 1) {
                        map.put(ruleId, sec);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
