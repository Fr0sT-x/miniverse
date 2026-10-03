package dev.frost.miniverse.minigame.impl.microparty;

import net.minecraft.nbt.NbtCompound;

import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public record MicroPartySettings(
    String mapId,
    int startingLives,
    int maxRounds,
    String gameMode,
    boolean speedScaling,
    int intermissionSeconds,
    Set<String> enabledRules
) {
    public MicroPartySettings(String mapId, int startingLives, int maxRounds, String gameMode, boolean speedScaling, int intermissionSeconds) {
        this(mapId, startingLives, maxRounds, gameMode, speedScaling, intermissionSeconds, Set.of());
    }

    public static MicroPartySettings defaults() {
        return new MicroPartySettings("", 3, 25, "SURVIVAL", true, 2, Set.of());
    }

    public boolean isRuleEnabled(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return false;
        }
        return this.enabledRules == null || this.enabledRules.isEmpty() || this.enabledRules.contains(ruleId);
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

        return new MicroPartySettings(
            nbt.contains("mapId") ? nbt.getString("mapId") : "",
            nbt.contains("startingLives") ? nbt.getInt("startingLives") : 3,
            nbt.contains("maxRounds") ? nbt.getInt("maxRounds") : 25,
            nbt.contains("gameMode") ? nbt.getString("gameMode") : "SURVIVAL",
            !nbt.contains("speedScaling") || nbt.getBoolean("speedScaling"),
            nbt.contains("intermissionSeconds") ? nbt.getInt("intermissionSeconds") : 2,
            Set.copyOf(rules)
        );
    }

    public void writeTo(Properties properties) {
        properties.setProperty("microparty.mapId", this.mapId != null ? this.mapId : "");
        properties.setProperty("microparty.startingLives", String.valueOf(this.startingLives));
        properties.setProperty("microparty.maxRounds", String.valueOf(this.maxRounds));
        properties.setProperty("microparty.gameMode", this.gameMode != null ? this.gameMode : "SURVIVAL");
        properties.setProperty("microparty.speedScaling", String.valueOf(this.speedScaling));
        properties.setProperty("microparty.intermissionSeconds", String.valueOf(this.intermissionSeconds));
        properties.setProperty("microparty.enabledRules", this.enabledRules != null ? String.join(",", this.enabledRules) : "");
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

        return new MicroPartySettings(
            properties.getProperty("microparty.mapId", ""),
            parseInt(properties.getProperty("microparty.startingLives"), 3),
            parseInt(properties.getProperty("microparty.maxRounds"), 25),
            properties.getProperty("microparty.gameMode", "SURVIVAL"),
            Boolean.parseBoolean(properties.getProperty("microparty.speedScaling", "true")),
            parseInt(properties.getProperty("microparty.intermissionSeconds"), 2),
            Set.copyOf(rules)
        );
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
}
