package dev.frost.miniverse.minigame.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.GameMode;

/**
 * Singleton holder for the active backend runtime.
 */
public class MinigameManager {
    private static final MinigameManager INSTANCE = new MinigameManager();

    @Nullable
    private MinigameRuntime runtime;
    private final Map<UUID, GameMode> gameModesBeforePause = new ConcurrentHashMap<>();

    private MinigameManager() {
    }

    /**
     * Gets the singleton instance of the MinigameManager.
     *
     * @return the MinigameManager instance
     */
    public static MinigameManager getInstance() {
        return INSTANCE;
    }

    /**
     * Sets the active minigame and initializes it.
     *
     * @param minigame the minigame to activate
     */
    public void setActiveMinigame(@Nullable Minigame minigame) {
        if (this.runtime != null) {
            this.runtime.stop();
            MatchLifecycleController.getInstance().reset();
            FreezeService.getInstance().clearAll();
            SpectatorService.getInstance().clearAll();
        }

        if (minigame != null) {
            this.runtime = new MinigameRuntime(minigame, null);
            this.runtime.initialize();
        } else {
            this.runtime = null;
        }
    }

    public void setActiveMinigame(@Nullable Minigame minigame, MinecraftServer server) {
        this.setActiveMinigame(minigame);
        this.bindServer(server);
    }

    /**
     * Gets the currently active minigame.
     *
     * @return the active minigame, or null if no minigame is active
     */
    @Nullable
    public Minigame getActiveMinigame() {
        return this.runtime == null ? null : this.runtime.minigame();
    }

    @Nullable
    public MinigameRuntime getRuntime() {
        return this.runtime;
    }

    @Nullable
    public MinigameContext getContext() {
        return this.runtime == null ? null : this.runtime.context();
    }

    public void bindServer(MinecraftServer server) {
        if (this.runtime != null) {
            this.runtime.bindServer(server);
        }
    }

    public void tickRuntimeClock(MinecraftServer server) {
        if (this.runtime != null && this.runtime.state() != GameState.PAUSED) {
            this.runtime.bindServer(server);
            this.runtime.context().clock().tick();
        }
    }

    public boolean pauseActiveGame() {
        if (this.runtime == null || !this.runtime.pause()) {
            return false;
        }
        for (ServerPlayerEntity player : this.runtime.context().liveParticipants()) {
            this.applyPauseGameMode(player);
            FreezeService.getInstance().freeze(player, FreezeReason.ADMIN_PAUSE);
        }
        MinigameSessionStore.save(this.runtime, MinigameSessionStore.SaveReason.PAUSE);
        return true;
    }

    public boolean resumeActiveGame() {
        if (this.runtime == null || !this.runtime.resume()) {
            return false;
        }
        for (ServerPlayerEntity player : this.runtime.context().liveParticipants()) {
            this.restorePausedGameMode(player);
            FreezeService.getInstance().unfreeze(player, FreezeReason.ADMIN_PAUSE);
        }
        this.gameModesBeforePause.clear();
        MinigameSessionStore.save(this.runtime, MinigameSessionStore.SaveReason.RESUME);
        return true;
    }

    public void applyPauseStateToParticipant(ServerPlayerEntity player) {
        if (this.runtime == null || this.runtime.state() != GameState.PAUSED || !this.isParticipant(player)) {
            return;
        }
        this.applyPauseGameMode(player);
        FreezeService.getInstance().freeze(player, FreezeReason.ADMIN_PAUSE);
    }

    /**
     * Gets the current game state.
     *
     * @return the current GameState, or null if no minigame is active
     */
    @Nullable
    public GameState getCurrentState() {
        return this.runtime == null ? null : this.runtime.state();
    }

    /**
     * Sets the current game state.
     *
     * @param state the new GameState
     */
    public void setCurrentState(GameState state) {
        if (this.runtime != null) {
            this.runtime.setState(state);
        }
    }

    /**
     * Checks if a minigame is currently active.
     *
     * @return true if a minigame is active, false otherwise
     */
    public boolean isMinigameActive() {
        return this.runtime != null && this.runtime.state() != null;
    }

    /**
     * Adds a player to the participant list.
     *
     * @param player the player to add
     */
    public void addParticipant(ServerPlayerEntity player) {
        if (this.runtime == null) {
            return;
        }
        this.bindPlayerServer(player);
        this.runtime.context().roster().add(player);
    }

    public void addParticipant(UUID playerId) {
        if (this.runtime != null) {
            this.runtime.context().roster().add(playerId);
        }
    }

    /**
     * Removes a player from the participant list.
     *
     * @param player the player to remove
     */
    public void removeParticipant(ServerPlayerEntity player) {
        this.removeParticipant(player.getUuid());
    }

    public void removeParticipant(UUID playerId) {
        if (this.runtime != null) {
            this.runtime.context().roster().remove(playerId);
        }
    }

    /**
     * Replaces an existing participant reference with a new player instance.
     * Useful for respawned players where the entity instance changes.
     *
     * @param oldPlayer the previous player entity
     * @param newPlayer the new player entity
     */
    public void replaceParticipant(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        if (this.runtime == null) {
            return;
        }
        this.bindPlayerServer(newPlayer);
        this.runtime.context().roster().add(newPlayer);
    }

    /**
     * Gets an immutable copy of the current participants list.
     *
     * @return a list of all participating players
     */
    public List<ServerPlayerEntity> getParticipants() {
        return this.runtime == null ? List.of() : this.runtime.context().liveParticipants();
    }

    public Set<UUID> getParticipantIds() {
        return this.runtime == null ? Set.of() : this.runtime.context().participantIds();
    }

    /**
     * Gets the number of current participants.
     *
     * @return the number of participants
     */
    public int getParticipantCount() {
        return this.runtime == null ? 0 : this.runtime.context().roster().size();
    }

    /**
     * Checks if a player is participating in the minigame.
     *
     * @param player the player to check
     * @return true if the player is a participant, false otherwise
     */
    public boolean isParticipant(ServerPlayerEntity player) {
        return this.isParticipant(player.getUuid());
    }

    public boolean isParticipant(UUID playerId) {
        return this.runtime != null && this.runtime.context().roster().contains(playerId);
    }

    /**
     * Clears all participants.
     */
    public void clearParticipants() {
        if (this.runtime != null) {
            this.runtime.context().roster().clear();
        }
    }

    /**
     * Resets the manager to its initial state.
     */
    public void reset() {
        if (this.runtime != null) {
            this.runtime.stop();
        }
        MatchLifecycleController.getInstance().reset();
        FreezeService.getInstance().clearAll();
        SpectatorService.getInstance().clearAll();
        this.gameModesBeforePause.clear();
        this.runtime = null;
    }

    private void applyPauseGameMode(ServerPlayerEntity player) {
        GameMode currentMode = player.interactionManager.getGameMode();
        this.gameModesBeforePause.putIfAbsent(player.getUuid(), currentMode);
        if (currentMode != GameMode.SPECTATOR) {
            player.changeGameMode(GameMode.ADVENTURE);
        }
    }

    private void restorePausedGameMode(ServerPlayerEntity player) {
        GameMode previousMode = this.gameModesBeforePause.remove(player.getUuid());
        if (previousMode != null) {
            player.changeGameMode(previousMode);
        }
    }

    private void bindPlayerServer(ServerPlayerEntity player) {
        if (this.runtime != null && player.getEntityWorld() instanceof ServerWorld world) {
            this.runtime.bindServer(world.getServer());
        }
    }
}
