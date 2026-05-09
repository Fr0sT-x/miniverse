package dev.frost.miniverse.minigame.core;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for handling the current active minigame and its state.
 * This is the central hub for all minigame-related state management.
 */
public class MinigameManager {
    private static final MinigameManager INSTANCE = new MinigameManager();

    @Nullable
    private Minigame activeMinigame;

    @Nullable
    private GameState currentState;

    private final Map<UUID, ServerPlayerEntity> participants = new ConcurrentHashMap<>();

    private MinigameManager() {
        this.currentState = null;
        this.activeMinigame = null;
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
        if (this.activeMinigame != null) {
            this.activeMinigame.stopGame();
        }

        this.activeMinigame = minigame;

        if (minigame != null) {
            this.currentState = GameState.WAITING_FOR_PLAYERS;
            minigame.initialize();
        } else {
            this.currentState = null;
            this.participants.clear();
        }
    }

    /**
     * Gets the currently active minigame.
     *
     * @return the active minigame, or null if no minigame is active
     */
    @Nullable
    public Minigame getActiveMinigame() {
        return this.activeMinigame;
    }

    /**
     * Gets the current game state.
     *
     * @return the current GameState, or null if no minigame is active
     */
    @Nullable
    public GameState getCurrentState() {
        return this.currentState;
    }

    /**
     * Sets the current game state.
     *
     * @param state the new GameState
     */
    public void setCurrentState(GameState state) {
        this.currentState = state;
        if (this.activeMinigame != null) {
            this.activeMinigame.setState(state);
        }
    }

    /**
     * Checks if a minigame is currently active.
     *
     * @return true if a minigame is active, false otherwise
     */
    public boolean isMinigameActive() {
        return this.activeMinigame != null && this.currentState != null;
    }

    /**
     * Adds a player to the participant list.
     *
     * @param player the player to add
     */
    public void addParticipant(ServerPlayerEntity player) {
        this.participants.put(player.getUuid(), player);
    }

    /**
     * Removes a player from the participant list.
     *
     * @param player the player to remove
     */
    public void removeParticipant(ServerPlayerEntity player) {
        this.participants.remove(player.getUuid());
    }

    /**
     * Replaces an existing participant reference with a new player instance.
     * Useful for respawned players where the entity instance changes.
     *
     * @param oldPlayer the previous player entity
     * @param newPlayer the new player entity
     */
    public void replaceParticipant(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        this.participants.remove(oldPlayer.getUuid());
        this.participants.put(newPlayer.getUuid(), newPlayer);
    }

    /**
     * Gets an immutable copy of the current participants list.
     *
     * @return a list of all participating players
     */
    public List<ServerPlayerEntity> getParticipants() {
        return new ArrayList<>(this.participants.values());
    }

    /**
     * Gets the number of current participants.
     *
     * @return the number of participants
     */
    public int getParticipantCount() {
        return this.participants.size();
    }

    /**
     * Checks if a player is participating in the minigame.
     *
     * @param player the player to check
     * @return true if the player is a participant, false otherwise
     */
    public boolean isParticipant(ServerPlayerEntity player) {
        return this.participants.containsKey(player.getUuid());
    }

    /**
     * Clears all participants.
     */
    public void clearParticipants() {
        this.participants.clear();
    }

    /**
     * Resets the manager to its initial state.
     */
    public void reset() {
        if (this.activeMinigame != null) {
            this.activeMinigame.stopGame();
        }
        this.activeMinigame = null;
        this.currentState = null;
        this.participants.clear();
    }
}
