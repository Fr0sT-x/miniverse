package dev.frost.miniverse.client.gui;

import java.util.List;

public final class SessionSnapshotData {
    public record SessionSummary(String id, String game, String state, long seed, int players) {
    }

    public record RosterEntry(String uuid, String name) {
    }

    public record GameMetadata(
        String id,
        String displayName,
        String description,
        String icon,
        String topology,
        String setupKind,
        boolean enabled,
        List<SetupField> fields
    ) {
        public boolean isGenericSetup() {
            return "generic".equalsIgnoreCase(this.setupKind);
        }
    }

    public record SetupField(
        String key,
        String label,
        String type,
        String defaultValue,
        boolean required,
        int min,
        int max
    ) {
        public boolean isBoolean() {
            return "boolean".equalsIgnoreCase(this.type);
        }

        public boolean isInteger() {
            return "integer".equalsIgnoreCase(this.type);
        }
    }

    public record MemorySettings(int maxHeapGb, int initialHeapGb, boolean enabled) {
    }

    public record ServerSettings(int viewDistance, int simulationDistance, boolean onlineMode, int spawnProtection, String difficulty, boolean allowFlight, boolean acceptsTransfers) {
    }

    private static volatile List<SessionSummary> sessions = List.of();
    private static volatile List<RosterEntry> roster = List.of();
    private static volatile List<GameMetadata> games = List.of();
    private static volatile int maxConcurrentLaunches = 2;
    private static volatile int launcherQueueCapacity = 64;
    private static volatile MemorySettings memorySettings = new MemorySettings(2, 1, true);
    private static volatile ServerSettings serverSettings = new ServerSettings(16, 8, false, 0, "easy", true, true);

    private SessionSnapshotData() {
    }

    public static List<SessionSummary> sessions() {
        return sessions;
    }

    public static List<RosterEntry> roster() {
        return roster;
    }

    public static List<GameMetadata> games() {
        return games;
    }

    public static int maxConcurrentLaunches() {
        return maxConcurrentLaunches;
    }

    public static int launcherQueueCapacity() {
        return launcherQueueCapacity;
    }

    public static MemorySettings memorySettings() {
        return memorySettings;
    }

    public static ServerSettings serverSettings() {
        return serverSettings;
    }

    public static void update(List<SessionSummary> newSessions, List<RosterEntry> newRoster, List<GameMetadata> newGames, int newMaxConcurrentLaunches, int newLauncherQueueCapacity, MemorySettings newMemorySettings, ServerSettings newServerSettings) {
        sessions = List.copyOf(newSessions);
        roster = List.copyOf(newRoster);
        games = List.copyOf(newGames);
        maxConcurrentLaunches = Math.clamp(newMaxConcurrentLaunches, 1, 64);
        launcherQueueCapacity = Math.max(1, newLauncherQueueCapacity);
        memorySettings = newMemorySettings;
        serverSettings = newServerSettings;
    }

    public static void update(List<SessionSummary> newSessions, List<RosterEntry> newRoster, List<GameMetadata> newGames, int newMaxConcurrentLaunches, int newLauncherQueueCapacity) {
        update(newSessions, newRoster, newGames, newMaxConcurrentLaunches, newLauncherQueueCapacity, memorySettings, serverSettings);
    }
}

