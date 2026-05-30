package dev.frost.miniverse.minigame.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.persistence.PlayerStateStore;
import dev.frost.miniverse.minigame.core.persistence.SessionData;
import dev.frost.miniverse.minigame.core.session.MinigameSessionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MinigameSessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "miniverse-game-session.json";
    private static final String BACKUP_FILE_NAME = FILE_NAME + ".bak";
    private static final int AUTOSAVE_INTERVAL_TICKS = 30 * 20;
    private static long lastAutosaveTick = -AUTOSAVE_INTERVAL_TICKS;
    private static final Map<UUID, Integer> LAST_PLAYER_RESTORE_TICK = new ConcurrentHashMap<>();

    private MinigameSessionStore() {
    }

    public static boolean saveActiveRuntime() {
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        return runtime != null && save(runtime);
    }

    public static boolean save(MinigameRuntime runtime) {
        return save(runtime, SaveReason.MANUAL);
    }

    public static synchronized boolean save(MinigameRuntime runtime, SaveReason reason) {
        if (runtime == null) {
            return false;
        }

        JsonObject root;
        if (runtime.minigame() instanceof PersistentMinigame persistent) {
            SessionData data = persistent.saveSessionData(runtime);
            MinigameSessionManager.getInstance().create(data, runtime);
            root = MinigameSessionManager.getInstance().serialize(data);
        } else {
            root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("game", runtime.minigame().getName());
            root.addProperty("state", runtime.state().name());
            root.addProperty("clockTicks", runtime.context().clock().ticks());
        }
        Path path = savePath();
        Optional<JsonObject> previous = readFrom(path, false);
        root.add("metadata", metadata(runtime, reason));
        root.add("lifecycle", MatchLifecycleController.getInstance().saveState(runtime));
        root.add("playerStates", PlayerStateStore.capture(runtime, previous
            .filter(object -> object.has("playerStates") && object.get("playerStates").isJsonArray())
            .map(object -> object.getAsJsonArray("playerStates"))
            .orElse(null)));

        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                Files.copy(path, path.resolveSibling(BACKUP_FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
            }
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to save minigame session state to {}.", path, e);
            return false;
        }
    }

    public static boolean loadInto(MinigameRuntime runtime) {
        return loadInto(runtime, MatchLifecycleOptions.defaults(runtime == null ? "Minigame" : runtime.minigame().getName()), null);
    }

    public static boolean loadInto(MinigameRuntime runtime, MatchLifecycleOptions lifecycleOptions, Runnable startCallback) {
        if (runtime == null) {
            return false;
        }

        Optional<JsonObject> saved = read();
        if (saved.isEmpty()) {
            return false;
        }

        JsonObject root = saved.get();
        normalizeRestoredState(root);
        if (runtime.minigame() instanceof PersistentMinigame persistent) {
            SessionData data = MinigameSessionManager.getInstance().deserialize(root);
            runtime.context().clock().setTicks(data.gameTicks());
            data.participantIds().forEach(runtime.context().participants()::add);
            persistent.loadSessionData(data);
            runtime.setState(data.gameState());
            MinigameSessionManager.getInstance().create(data, runtime);
        } else {
            restoredGameState(root).ifPresent(runtime::setState);
            restoredClockTicks(root).ifPresent(runtime.context().clock()::setTicks);
            restoreParticipantIds(root, runtime);
        }
        if (root.has("lifecycle") && root.get("lifecycle").isJsonObject()) {
            MatchLifecycleController.getInstance().restoreState(runtime, lifecycleOptions, root.getAsJsonObject("lifecycle"), startCallback);
        }
        if (root.has("playerStates") && root.get("playerStates").isJsonArray()) {
            int restored = PlayerStateStore.restore(runtime, root.getAsJsonArray("playerStates"));
            if (restored > 0) {
                Miniverse.LOGGER.info("Restored {} player state snapshot(s) from {}.", restored, savePath());
            }
        }
        return true;
    }

    public static boolean restorePlayerState(MinigameRuntime runtime, ServerPlayerEntity player) {
        if (runtime == null || player == null) {
            return false;
        }
        MinecraftServer server = runtime.context().nullableServer();
        if (server != null) {
            int currentTick = server.getTicks();
            Integer previousTick = LAST_PLAYER_RESTORE_TICK.put(player.getUuid(), currentTick);
            if (previousTick != null && previousTick == currentTick) {
                return false;
            }
        }

        Optional<JsonObject> saved = read();
        if (saved.isEmpty()) {
            return false;
        }

        JsonObject root = saved.get();
        if (!root.has("playerStates") || !root.get("playerStates").isJsonArray()) {
            return false;
        }

        boolean restored = PlayerStateStore.restore(runtime, root.getAsJsonArray("playerStates"), player);
        if (restored) {
            Miniverse.LOGGER.info("Restored reconnect player state for {} from {}.", player.getName().getString(), savePath());
        }
        return restored;
    }

    public static boolean restorePlayerStateIfPresent(MinigameRuntime runtime, ServerPlayerEntity player) {
        return restorePlayerState(runtime, player);
    }

    public static Optional<GameState> restoredGameState() {
        Optional<JsonObject> saved = read();
        if (saved.isEmpty()) {
            return Optional.empty();
        }
        JsonObject root = saved.get();
        normalizeRestoredState(root);
        return restoredGameState(root);
    }

    public static boolean hasRestoredActiveOrPausedState() {
        Optional<JsonObject> saved = read();
        if (saved.isEmpty()) {
            return false;
        }
        JsonObject root = saved.get();
        normalizeRestoredState(root);
        return restoredGameState(root)
            .map(state -> state.isActive()
                || state == GameState.PAUSED
                || state == GameState.STARTING
                || state == GameState.FROZEN)
            .orElse(false);
    }

    public static void tick(MinecraftServer server) {
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime == null) {
            return;
        }
        long now = server.getTicks();
        if (now - lastAutosaveTick < AUTOSAVE_INTERVAL_TICKS) {
            return;
        }
        lastAutosaveTick = now;
        save(runtime, SaveReason.AUTOSAVE);
    }

    public static void saveOnShutdown() {
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            save(runtime, SaveReason.SHUTDOWN);
        }
    }

    public static Optional<JsonObject> read() {
        return readFrom(savePath(), true);
    }

    public static Optional<JsonObject> readFrom(Path path, boolean quarantineInvalid) {
        if (path == null || !Files.exists(path)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            Miniverse.LOGGER.warn("Failed to read minigame session state from {}.", path, e);
            if (quarantineInvalid) {
                quarantineCorruptSave(path);
            }
        }

        Path backup = path.resolveSibling(BACKUP_FILE_NAME);
        if (!path.equals(backup) && Files.exists(backup)) {
            Miniverse.LOGGER.warn("Trying backup minigame session state from {}.", backup);
            return readFrom(backup, false);
        }
        return Optional.empty();
    }

    public static Path savePath() {
        String configPath = System.getProperty("miniverse.session.config", "");
        if (!configPath.isBlank()) {
            Path config = Paths.get(configPath).toAbsolutePath().normalize();
            Path parent = config.getParent();
            if (parent != null) {
                return parent.resolve(FILE_NAME);
            }
        }
        return Paths.get("").toAbsolutePath().normalize().resolve(FILE_NAME);
    }

    private static void normalizeRestoredState(JsonObject root) {
        if (root == null || !hasSavedPlayerStates(root)) {
            return;
        }

        String state = stringValue(root, "gameState", stringValue(root, "state", ""));
        if (!"WAITING_FOR_PLAYERS".equals(state) && !"WAITING".equals(state)) {
            return;
        }

        if (!hasParticipantRoster(root)) {
            return;
        }

        root.addProperty("state", GameState.RUNNING.name());
        root.addProperty("gameState", GameState.RUNNING.name());
        if (root.has("runtime") && root.get("runtime").isJsonObject()) {
            root.getAsJsonObject("runtime").addProperty("state", GameState.RUNNING.name());
        }
        Miniverse.LOGGER.info("Normalized retained session save from {} to RUNNING because it contains persisted player state.", state);
    }

    private static boolean hasSavedPlayerStates(JsonObject root) {
        return root.has("playerStates") && root.get("playerStates").isJsonArray() && !root.getAsJsonArray("playerStates").isEmpty();
    }

    private static boolean hasParticipantRoster(JsonObject root) {
        if (root.has("participantIds") && root.get("participantIds").isJsonArray() && !root.getAsJsonArray("participantIds").isEmpty()) {
            return true;
        }
        return root.has("participants") && root.get("participants").isJsonArray() && !root.getAsJsonArray("participants").isEmpty();
    }

    private static String stringValue(JsonObject root, String key, String fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : fallback;
    }

    private static Optional<GameState> restoredGameState(JsonObject root) {
        String stateName = stringValue(root, "gameState", stringValue(root, "state", ""));
        if (root.has("runtime") && root.get("runtime").isJsonObject()) {
            stateName = stringValue(root.getAsJsonObject("runtime"), "state", stateName);
        }
        try {
            return stateName.isBlank() ? Optional.empty() : Optional.of(GameState.valueOf(stateName));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Long> restoredClockTicks(JsonObject root) {
        if (root.has("gameTicks") && root.get("gameTicks").isJsonPrimitive()) {
            return Optional.of(root.get("gameTicks").getAsLong());
        }
        if (root.has("clockTicks") && root.get("clockTicks").isJsonPrimitive()) {
            return Optional.of(root.get("clockTicks").getAsLong());
        }
        JsonObject metadata = root.has("metadata") && root.get("metadata").isJsonObject() ? root.getAsJsonObject("metadata") : null;
        if (metadata != null && metadata.has("clockTicks") && metadata.get("clockTicks").isJsonPrimitive()) {
            return Optional.of(metadata.get("clockTicks").getAsLong());
        }
        return Optional.empty();
    }

    private static void restoreParticipantIds(JsonObject root, MinigameRuntime runtime) {
        if (runtime == null) {
            return;
        }
        if (root.has("participantIds") && root.get("participantIds").isJsonArray()) {
            restoreParticipantIds(root.getAsJsonArray("participantIds"), runtime);
            return;
        }
        if (root.has("participants") && root.get("participants").isJsonArray()) {
            restoreParticipantIds(root.getAsJsonArray("participants"), runtime);
        }
    }

    private static void restoreParticipantIds(com.google.gson.JsonArray participantIds, MinigameRuntime runtime) {
        for (var element : participantIds) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            try {
                runtime.context().participants().add(java.util.UUID.fromString(element.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static String fileName() {
        return FILE_NAME;
    }

    public static String backupFileName() {
        return BACKUP_FILE_NAME;
    }

    private static void quarantineCorruptSave(Path path) {
        try {
            String fileName = path.getFileName().toString();
            Path corrupt = path.resolveSibling(fileName + ".corrupt-" + System.currentTimeMillis());
            Files.move(path, corrupt, StandardCopyOption.REPLACE_EXISTING);
            Miniverse.LOGGER.warn("Moved corrupt minigame session save {} to {}.", path, corrupt);
        } catch (IOException moveError) {
            Miniverse.LOGGER.warn("Failed to quarantine corrupt minigame session save {}.", path, moveError);
        }
    }

    private static JsonObject metadata(MinigameRuntime runtime, SaveReason reason) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("formatVersion", 3);
        metadata.addProperty("saveReason", reason.name());
        metadata.addProperty("savedAt", Instant.now().toString());
        metadata.addProperty("gameType", runtime.minigame().getName());
        metadata.addProperty("gameState", runtime.state().name());
        metadata.addProperty("clockTicks", runtime.context().clock().ticks());
        metadata.addProperty("participantCount", runtime.context().participantIds().size());
        return metadata;
    }

    public enum SaveReason {
        MANUAL,
        AUTOSAVE,
        PAUSE,
        RESUME,
        DISCONNECT,
        RECONNECT,
        SHUTDOWN
    }
}
