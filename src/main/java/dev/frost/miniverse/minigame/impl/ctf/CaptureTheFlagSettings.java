package dev.frost.miniverse.minigame.impl.ctf;

import net.minecraft.nbt.NbtCompound;

import java.util.Properties;

public record CaptureTheFlagSettings(
    String mapId,
    boolean eliminationMode,
    int targetCaptures,
    int matchDurationMinutes,
    int respawnDelaySeconds,
    int flagReturnDelaySeconds,
    boolean requireOwnFlagAtBase,
    boolean carrierGlowing,
    boolean teamChatEnabled,
    boolean allowInvisibilityPotion,
    boolean naturalRegeneration,
    boolean suddenDeath
) {
    public CaptureTheFlagSettings(
        String mapId,
        boolean eliminationMode,
        int targetCaptures,
        int matchDurationMinutes,
        int respawnDelaySeconds,
        int flagReturnDelaySeconds,
        boolean requireOwnFlagAtBase,
        boolean carrierGlowing,
        boolean teamChatEnabled
    ) {
        this(
            mapId,
            eliminationMode,
            targetCaptures,
            matchDurationMinutes,
            respawnDelaySeconds,
            flagReturnDelaySeconds,
            requireOwnFlagAtBase,
            carrierGlowing,
            teamChatEnabled,
            true,
            true,
            true
        );
    }

    public CaptureTheFlagSettings(
        String mapId,
        boolean eliminationMode,
        int targetCaptures,
        int matchDurationMinutes,
        int respawnDelaySeconds,
        int flagReturnDelaySeconds,
        boolean requireOwnFlagAtBase,
        boolean carrierGlowing,
        boolean teamChatEnabled,
        boolean allowInvisibilityPotion,
        boolean naturalRegeneration
    ) {
        this(
            mapId,
            eliminationMode,
            targetCaptures,
            matchDurationMinutes,
            respawnDelaySeconds,
            flagReturnDelaySeconds,
            requireOwnFlagAtBase,
            carrierGlowing,
            teamChatEnabled,
            allowInvisibilityPotion,
            naturalRegeneration,
            true
        );
    }

    public static CaptureTheFlagSettings defaults() {
        return new CaptureTheFlagSettings(
            "",
            false,  // eliminationMode (default OFF)
            3,      // targetCaptures
            15,     // matchDurationMinutes
            5,      // respawnDelaySeconds
            15,     // flagReturnDelaySeconds
            true,   // requireOwnFlagAtBase
            true,   // carrierGlowing
            false,  // teamChatEnabled
            true,   // allowInvisibilityPotion
            true,   // naturalRegeneration
            true    // suddenDeath
        );
    }

    public static CaptureTheFlagSettings fromNbt(NbtCompound nbt) {
        if (nbt == null) {
            return defaults();
        }
        return new CaptureTheFlagSettings(
            nbt.contains("mapId") ? nbt.getString("mapId") : "",
            nbt.contains("eliminationMode") && nbt.getBoolean("eliminationMode"),
            nbt.contains("targetCaptures") ? nbt.getInt("targetCaptures") : 3,
            nbt.contains("matchDurationMinutes") ? nbt.getInt("matchDurationMinutes") : 15,
            nbt.contains("respawnDelaySeconds") ? nbt.getInt("respawnDelaySeconds") : 5,
            nbt.contains("flagReturnDelaySeconds") ? nbt.getInt("flagReturnDelaySeconds") : 15,
            !nbt.contains("requireOwnFlagAtBase") || nbt.getBoolean("requireOwnFlagAtBase"),
            !nbt.contains("carrierGlowing") || nbt.getBoolean("carrierGlowing"),
            nbt.contains("teamChatEnabled") && nbt.getBoolean("teamChatEnabled"),
            !nbt.contains("allowInvisibilityPotion") || nbt.getBoolean("allowInvisibilityPotion"),
            !nbt.contains("naturalRegeneration") || nbt.getBoolean("naturalRegeneration"),
            !nbt.contains("suddenDeath") || nbt.getBoolean("suddenDeath")
        );
    }

    public void writeTo(Properties properties) {
        properties.setProperty("ctf.mapId", this.mapId);
        properties.setProperty("ctf.eliminationMode", String.valueOf(this.eliminationMode));
        properties.setProperty("ctf.targetCaptures", String.valueOf(this.targetCaptures));
        properties.setProperty("ctf.matchDurationMinutes", String.valueOf(this.matchDurationMinutes));
        properties.setProperty("ctf.respawnDelaySeconds", String.valueOf(this.respawnDelaySeconds));
        properties.setProperty("ctf.flagReturnDelaySeconds", String.valueOf(this.flagReturnDelaySeconds));
        properties.setProperty("ctf.requireOwnFlagAtBase", String.valueOf(this.requireOwnFlagAtBase));
        properties.setProperty("ctf.carrierGlowing", String.valueOf(this.carrierGlowing));
        properties.setProperty("ctf.teamChatEnabled", String.valueOf(this.teamChatEnabled));
        properties.setProperty("ctf.allowInvisibilityPotion", String.valueOf(this.allowInvisibilityPotion));
        properties.setProperty("ctf.naturalRegeneration", String.valueOf(this.naturalRegeneration));
        properties.setProperty("ctf.suddenDeath", String.valueOf(this.suddenDeath));
    }

    public static CaptureTheFlagSettings fromProperties(Properties properties) {
        if (properties == null) {
            return defaults();
        }
        return new CaptureTheFlagSettings(
            properties.getProperty("ctf.mapId", ""),
            Boolean.parseBoolean(properties.getProperty("ctf.eliminationMode", "false")),
            Integer.parseInt(properties.getProperty("ctf.targetCaptures", "3")),
            Integer.parseInt(properties.getProperty("ctf.matchDurationMinutes", "15")),
            Integer.parseInt(properties.getProperty("ctf.respawnDelaySeconds", "5")),
            Integer.parseInt(properties.getProperty("ctf.flagReturnDelaySeconds", "15")),
            Boolean.parseBoolean(properties.getProperty("ctf.requireOwnFlagAtBase", "true")),
            Boolean.parseBoolean(properties.getProperty("ctf.carrierGlowing", "true")),
            Boolean.parseBoolean(properties.getProperty("ctf.teamChatEnabled", "false")),
            Boolean.parseBoolean(properties.getProperty("ctf.allowInvisibilityPotion", "true")),
            Boolean.parseBoolean(properties.getProperty("ctf.naturalRegeneration", "true")),
            Boolean.parseBoolean(properties.getProperty("ctf.suddenDeath", "true"))
        );
    }

    public void writeTo(NbtCompound nbt) {
        nbt.putString("mapId", this.mapId);
        nbt.putBoolean("eliminationMode", this.eliminationMode);
        nbt.putInt("targetCaptures", this.targetCaptures);
        nbt.putInt("matchDurationMinutes", this.matchDurationMinutes);
        nbt.putInt("respawnDelaySeconds", this.respawnDelaySeconds);
        nbt.putInt("flagReturnDelaySeconds", this.flagReturnDelaySeconds);
        nbt.putBoolean("requireOwnFlagAtBase", this.requireOwnFlagAtBase);
        nbt.putBoolean("carrierGlowing", this.carrierGlowing);
        nbt.putBoolean("teamChatEnabled", this.teamChatEnabled);
        nbt.putBoolean("allowInvisibilityPotion", this.allowInvisibilityPotion);
        nbt.putBoolean("naturalRegeneration", this.naturalRegeneration);
        nbt.putBoolean("suddenDeath", this.suddenDeath);
    }
}
