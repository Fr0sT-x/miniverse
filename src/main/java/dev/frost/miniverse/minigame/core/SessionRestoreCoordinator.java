package dev.frost.miniverse.minigame.core;

import com.google.gson.JsonObject;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.session.BackendLaunchMode;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;

public final class SessionRestoreCoordinator {
    private SessionRestoreCoordinator() {
    }

    public static BackendLaunchMode launchMode() {
        BackendLaunchMode configured = SessionRuntimeConfig.getLaunchMode();
        if (configured == BackendLaunchMode.RESTORE_SESSION || configured == BackendLaunchMode.INSPECTION_SESSION) {
            return configured;
        }
        return MinigameSessionStore.read().isPresent() ? BackendLaunchMode.RESTORE_SESSION : BackendLaunchMode.NEW_SESSION;
    }

    public static boolean isRestoreSession() {
        return launchMode() == BackendLaunchMode.RESTORE_SESSION;
    }

    public static boolean restoreRuntimeIfPresent(MinigameRuntime runtime, MatchLifecycleOptions options, Runnable startCallback) {
        if (runtime == null || !isRestoreSession()) {
            return false;
        }
        boolean restored = MinigameSessionStore.loadInto(runtime, options, startCallback);
        if (restored) {
            Miniverse.LOGGER.info("Loaded saved runtime state for {} from {}.", runtime.minigame().getName(), MinigameSessionStore.savePath());
            logDiagnostics();
        }
        return restored;
    }

    public static boolean hasRestoredActiveOrPausedState() {
        return isRestoreSession() && MinigameSessionStore.hasRestoredActiveOrPausedState();
    }

    public static boolean restorePlayerStateIfPresent(MinigameRuntime runtime, ServerPlayerEntity player) {
        return isRestoreSession() && MinigameSessionStore.restorePlayerStateIfPresent(runtime, player);
    }

    private static void logDiagnostics() {
        Optional<JsonObject> saved = MinigameSessionStore.read();
        if (saved.isEmpty()) {
            return;
        }
        JsonObject root = saved.get();
        JsonObject metadata = root.has("metadata") && root.get("metadata").isJsonObject()
            ? root.getAsJsonObject("metadata")
            : new JsonObject();
        int participantCount = arraySize(root, "participantIds", arraySize(root, "participants", 0));
        int snapshotCount = arraySize(root, "playerStates", 0);
        String game = string(root, "gameType", string(root, "game", string(metadata, "gameType", "unknown")));
        String state = string(root, "gameState", string(root, "state", string(metadata, "gameState", "unknown")));
        String lifecyclePhase = "unknown";
        if (root.has("lifecycle") && root.get("lifecycle").isJsonObject()) {
            lifecyclePhase = string(root.getAsJsonObject("lifecycle"), "phase", "unknown");
        }
        Miniverse.LOGGER.info(
            "Restore diagnostics: save={}, game={}, state={}, lifecyclePhase={}, participants={}, playerSnapshots={}.",
            MinigameSessionStore.savePath(),
            game,
            state,
            lifecyclePhase,
            participantCount,
            snapshotCount
        );
    }

    private static int arraySize(JsonObject object, String key, int fallback) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key).size() : fallback;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
}
