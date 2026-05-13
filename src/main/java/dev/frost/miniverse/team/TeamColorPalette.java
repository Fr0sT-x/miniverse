package dev.frost.miniverse.team;

import net.minecraft.util.Formatting;

import java.util.Locale;

public final class TeamColorPalette {
    private static final TeamColor[] COLORS = new TeamColor[] {
        new TeamColor(Formatting.AQUA, "CYAN"),
        new TeamColor(Formatting.DARK_PURPLE, "PURPLE"),
        new TeamColor(Formatting.GOLD, "GOLD"),
        new TeamColor(Formatting.DARK_GREEN, "PINE"),
        new TeamColor(Formatting.YELLOW, "YELLOW"),
        new TeamColor(Formatting.DARK_AQUA, "TEAL"),
        new TeamColor(Formatting.LIGHT_PURPLE, "LILAC"),
        new TeamColor(Formatting.GRAY, "GRAY"),
        new TeamColor(Formatting.BLACK, "BLACK"),
        new TeamColor(Formatting.GREEN, "LIME"),
        new TeamColor(Formatting.DARK_PURPLE, "BURGUNDY")
    };

    private TeamColorPalette() {
    }

    public static Formatting colorFor(String key) {
        return resolve(key).color();
    }

    public static String labelFor(String key) {
        return resolve(key).label();
    }

    public static String displayName(String key) {
        return "[" + labelFor(key) + "]";
    }

    private static TeamColor resolve(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            normalized = "team";
        }
        if (normalized.contains("spec") || normalized.contains("dead")) {
            return new TeamColor(Formatting.GRAY, "GRAY");
        }
        int index = Math.floorMod(normalized.hashCode(), COLORS.length);
        return COLORS[index];
    }

    private record TeamColor(Formatting color, String label) {
    }
}
