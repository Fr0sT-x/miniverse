package dev.frost.miniverse.client.gui;

import java.util.List;

public final class SessionSnapshotData {
    public record SessionSummary(String id, String game, String state, long seed, int players, long createdAtMillis, long launchedAtMillis, long updatedAtMillis, long playedMillis, boolean inspectable, boolean retained, List<GroupSummary> groups) {
    }

    public record GroupSummary(String label, String displayName, String state, int playerCount) {
    }

    public record RosterEntry(String uuid, String name) {
    }

    public record PendingJoiner(String uuid, String name, long joinedAtMillis) {
    }

    public record MapSummary(String id, String name, String description, String folder, long lastModifiedMillis, List<String> gamemodes, List<MapValidation> validations) {
        public boolean supports(String gameId) {
            return this.gamemodes.stream().anyMatch(id -> id.equalsIgnoreCase(gameId));
        }

        public boolean validFor(String gameId) {
            return this.validations.stream().anyMatch(validation -> validation.game().equalsIgnoreCase(gameId) && validation.valid());
        }
    }

    public record MapValidation(String game, boolean valid, List<String> errors) {
    }

    public record EditorExtension(String gameId, String displayName, List<EditorMarkerDefinition> markers) {
    }

    public record EditorMarkerDefinition(String key, String displayName, String type, String configKey, int minCount, int maxCount, String description) {
        public boolean single() {
            return this.maxCount == 1;
        }
    }

    public record EditorMarker(String id, String definitionKey, String name, String type, List<EditorPoint> points, List<EditorRegionPart> regions, com.google.gson.JsonObject properties) {
    }

    public record EditorRegionPart(EditorPoint min, EditorPoint max) {
    }

    public record EditorPoint(double x, double y, double z, float yaw, float pitch) {
    }

    public record EditorMarkerGroup(String definitionKey, List<EditorMarker> markers) {
    }

    public record EditorValidation(boolean valid, List<String> errors, List<String> warnings) {
    }

    public record EditorGameState(String gameId, List<EditorMarkerGroup> markerGroups, EditorValidation validation) {
    }

    public record EditorState(String mapId, List<EditorGameState> games) {
        public List<EditorMarker> markers(String gameId, String definitionKey) {
            return this.games.stream()
                .filter(game -> game.gameId().equalsIgnoreCase(gameId))
                .findFirst()
                .flatMap(game -> game.markerGroups().stream().filter(group -> group.definitionKey().equalsIgnoreCase(definitionKey)).findFirst())
                .map(EditorMarkerGroup::markers)
                .orElse(List.of());
        }
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

    public record ServerSettings(int viewDistance, int simulationDistance, boolean onlineMode, int spawnProtection, String difficulty, boolean allowFlight, boolean acceptsTransfers, String advertisedHost) {
    }

    public record RetentionSettings(int keepLatestSessions, int maxAgeDays) {
    }

    private static volatile List<SessionSummary> sessions = List.of();
    private static volatile List<RosterEntry> roster = List.of();
    private static volatile List<PendingJoiner> pendingJoiners = List.of();
    private static volatile List<MapSummary> maps = List.of();
    private static volatile boolean mapEditor = false;
    private static volatile List<EditorExtension> editorExtensions = List.of();
    private static volatile EditorState editorState = new EditorState("", List.of());
    private static volatile List<GameMetadata> games = List.of();
    private static volatile int maxConcurrentLaunches = 2;
    private static volatile int launcherQueueCapacity = 64;
    private static volatile MemorySettings memorySettings = new MemorySettings(2, 1, true);
    private static volatile ServerSettings serverSettings = new ServerSettings(16, 8, false, 0, "easy", true, true, "127.0.0.1");
    private static volatile RetentionSettings retentionSettings = new RetentionSettings(3, 7);
    private static volatile boolean sessionServer = false;

    private SessionSnapshotData() {
    }

    public static List<SessionSummary> sessions() {
        return sessions;
    }

    public static List<RosterEntry> roster() {
        return roster;
    }

    public static List<PendingJoiner> pendingJoiners() {
        return pendingJoiners;
    }

    public static List<MapSummary> maps() {
        return maps;
    }

    public static boolean mapEditor() {
        return mapEditor;
    }

    public static List<EditorExtension> editorExtensions() {
        return editorExtensions;
    }

    public static EditorState editorState() {
        return editorState;
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

    public static RetentionSettings retentionSettings() {
        return retentionSettings;
    }

    public static boolean sessionServer() {
        return sessionServer;
    }

    public static void update(List<SessionSummary> newSessions, List<RosterEntry> newRoster, List<GameMetadata> newGames, int newMaxConcurrentLaunches, int newLauncherQueueCapacity, MemorySettings newMemorySettings, ServerSettings newServerSettings, RetentionSettings newRetentionSettings, boolean newSessionServer) {
        update(newSessions, newRoster, pendingJoiners, newGames, newMaxConcurrentLaunches, newLauncherQueueCapacity, newMemorySettings, newServerSettings, newRetentionSettings, newSessionServer);
    }

    public static void update(List<SessionSummary> newSessions, List<RosterEntry> newRoster, List<PendingJoiner> newPendingJoiners, List<GameMetadata> newGames, int newMaxConcurrentLaunches, int newLauncherQueueCapacity, MemorySettings newMemorySettings, ServerSettings newServerSettings, RetentionSettings newRetentionSettings, boolean newSessionServer) {
        update(newSessions, newRoster, newPendingJoiners, maps, newGames, newMaxConcurrentLaunches, newLauncherQueueCapacity, newMemorySettings, newServerSettings, newRetentionSettings, newSessionServer);
    }

    public static void update(List<SessionSummary> newSessions, List<RosterEntry> newRoster, List<PendingJoiner> newPendingJoiners, List<MapSummary> newMaps, List<GameMetadata> newGames, int newMaxConcurrentLaunches, int newLauncherQueueCapacity, MemorySettings newMemorySettings, ServerSettings newServerSettings, RetentionSettings newRetentionSettings, boolean newSessionServer) {
        sessions = List.copyOf(newSessions);
        roster = List.copyOf(newRoster);
        pendingJoiners = List.copyOf(newPendingJoiners);
        maps = List.copyOf(newMaps);
        games = List.copyOf(newGames);
        maxConcurrentLaunches = Math.clamp(newMaxConcurrentLaunches, 1, 64);
        launcherQueueCapacity = Math.max(1, newLauncherQueueCapacity);
        memorySettings = newMemorySettings;
        serverSettings = newServerSettings;
        retentionSettings = newRetentionSettings;
        sessionServer = newSessionServer;
    }

    public static void updateEditor(boolean newMapEditor, List<EditorExtension> newEditorExtensions, EditorState newEditorState) {
        mapEditor = newMapEditor;
        editorExtensions = List.copyOf(newEditorExtensions == null ? List.of() : newEditorExtensions);
        editorState = newEditorState == null ? new EditorState("", List.of()) : newEditorState;
        dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE.editorActive = newMapEditor;
        if (!dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE.editorActive) {
            dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE.disabledOverlays.clear();
        }
    }

    public static void update(List<SessionSummary> newSessions, List<RosterEntry> newRoster, List<GameMetadata> newGames, int newMaxConcurrentLaunches, int newLauncherQueueCapacity) {
        update(newSessions, newRoster, newGames, newMaxConcurrentLaunches, newLauncherQueueCapacity, memorySettings, serverSettings, retentionSettings, sessionServer);
    }
}
