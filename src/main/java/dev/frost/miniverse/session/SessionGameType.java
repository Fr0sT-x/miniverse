package dev.frost.miniverse.session;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SessionGameType {
    MANHUNT("manhunt", "Manhunt", SessionTopology.SHARED_WORLD),
    SPEEDRUN("speedrun", "Speedrun", SessionTopology.ISOLATED_WORLD),
    BOUNTY_HUNT("bountyhunt", "Bounty Hunt", SessionTopology.SHARED_WORLD),
    RESOURCE_SPRINT("resource_sprint", "Resource Sprint", SessionTopology.SHARED_WORLD),
    DEATH_SWAP("deathswap", "Death Swap", SessionTopology.SHARED_WORLD);

    private final String commandName;
    private final String displayName;
    private final SessionTopology topology;

    SessionGameType(String commandName, String displayName, SessionTopology topology) {
        this.commandName = commandName;
        this.displayName = displayName;
        this.topology = topology;
    }

    public String getCommandName() {
        return this.commandName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public SessionTopology getTopology() {
        return this.topology;
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

