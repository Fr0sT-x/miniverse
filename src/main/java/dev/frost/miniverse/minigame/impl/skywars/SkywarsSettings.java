package dev.frost.miniverse.minigame.impl.skywars;

import net.minecraft.nbt.NbtCompound;
import java.util.Properties;

public record SkywarsSettings(
    String mode,
    int refillIntervalSeconds,
    int cageTimerSeconds,
    int timeLimitSeconds,
    boolean instantVoidDeath,
    boolean teamChatEnabled
) {
    public SkywarsSettings(String mode, int refillIntervalSeconds, int cageTimerSeconds, int timeLimitSeconds, boolean instantVoidDeath) {
        this(mode, refillIntervalSeconds, cageTimerSeconds, timeLimitSeconds, instantVoidDeath, false);
    }

    public static final String MODE_NORMAL = "normal";
    public static final String MODE_INSANE = "insane";

    public static final int DEFAULT_REFILL_INTERVAL = 180;
    public static final int DEFAULT_CAGE_TIMER = 10;
    public static final int DEFAULT_TIME_LIMIT = 600;

    public static SkywarsSettings defaults() {
        return new SkywarsSettings(MODE_NORMAL, DEFAULT_REFILL_INTERVAL, DEFAULT_CAGE_TIMER, DEFAULT_TIME_LIMIT, true, false);
    }

    public boolean isInsaneMode() {
        return MODE_INSANE.equalsIgnoreCase(mode);
    }

    public static SkywarsSettings fromNbt(NbtCompound nbt) {
        if (nbt == null || nbt.isEmpty()) {
            return defaults();
        }
        String mode = nbt.contains("mode") ? nbt.getString("mode") : MODE_NORMAL;
        int refill = nbt.contains("refillIntervalSeconds") ? nbt.getInt("refillIntervalSeconds") : DEFAULT_REFILL_INTERVAL;
        int cage = nbt.contains("cageTimerSeconds") ? nbt.getInt("cageTimerSeconds") : DEFAULT_CAGE_TIMER;
        int timeLimit = nbt.contains("timeLimitSeconds") ? nbt.getInt("timeLimitSeconds") : DEFAULT_TIME_LIMIT;
        boolean instantVoid = !nbt.contains("instantVoidDeath") || nbt.getBoolean("instantVoidDeath");
        boolean teamChat = nbt.contains("teamChatEnabled") && nbt.getBoolean("teamChatEnabled");
        return new SkywarsSettings(mode, refill, cage, timeLimit, instantVoid, teamChat);
    }

    public static SkywarsSettings fromProperties(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return defaults();
        }
        String mode = properties.getProperty("skywars.mode", MODE_NORMAL);
        int refill = parseInt(properties.getProperty("skywars.refillIntervalSeconds"), DEFAULT_REFILL_INTERVAL);
        int cage = parseInt(properties.getProperty("skywars.cageTimerSeconds"), DEFAULT_CAGE_TIMER);
        int timeLimit = parseInt(properties.getProperty("skywars.timeLimitSeconds"), DEFAULT_TIME_LIMIT);
        boolean instantVoid = Boolean.parseBoolean(properties.getProperty("skywars.instantVoidDeath", "true"));
        boolean teamChat = Boolean.parseBoolean(properties.getProperty("skywars.teamChatEnabled", "false"));
        return new SkywarsSettings(mode, refill, cage, timeLimit, instantVoid, teamChat);
    }

    public void writeTo(Properties properties) {
        properties.setProperty("skywars.mode", mode);
        properties.setProperty("skywars.refillIntervalSeconds", String.valueOf(refillIntervalSeconds));
        properties.setProperty("skywars.cageTimerSeconds", String.valueOf(cageTimerSeconds));
        properties.setProperty("skywars.timeLimitSeconds", String.valueOf(timeLimitSeconds));
        properties.setProperty("skywars.instantVoidDeath", String.valueOf(instantVoidDeath));
        properties.setProperty("skywars.teamChatEnabled", String.valueOf(teamChatEnabled));
    }

    public void writeTo(NbtCompound nbt) {
        nbt.putString("mode", mode);
        nbt.putInt("refillIntervalSeconds", refillIntervalSeconds);
        nbt.putInt("cageTimerSeconds", cageTimerSeconds);
        nbt.putInt("timeLimitSeconds", timeLimitSeconds);
        nbt.putBoolean("instantVoidDeath", instantVoidDeath);
        nbt.putBoolean("teamChatEnabled", teamChatEnabled);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
