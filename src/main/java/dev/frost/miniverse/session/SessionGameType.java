package dev.frost.miniverse.session;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SessionGameType {
    MANHUNT("manhunt", "Manhunt"),
    SPEEDRUN("speedrun", "Speedrun");

    private final String commandName;
    private final String displayName;

    SessionGameType(String commandName, String displayName) {
        this.commandName = commandName;
        this.displayName = displayName;
    }

    public String getCommandName() {
        return this.commandName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public static Optional<SessionGameType> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(type -> type.commandName.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized))
            .findFirst();
    }
}

