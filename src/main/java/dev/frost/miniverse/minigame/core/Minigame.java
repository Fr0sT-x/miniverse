package dev.frost.miniverse.minigame.core;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Core interface for all minigame implementations.
 * Defines the contract that all minigames must follow.
 */
public interface Minigame {

    /**
     * Called when the minigame is being set up.
     * Initialize all necessary game state here.
     */
    void initialize();

    /**
     * Called when the minigame should start.
     * All players should be set up and ready to play.
     */
    void startGame();

    /**
     * Called when the minigame should stop.
     * Clean up all resources and reset state.
     */
    void stopGame();

    /**
     * Called when a player dies during the minigame.
     *
     * @param player the player who died
     */
    void onPlayerDeath(ServerPlayerEntity player);

    /**
     * Gets the name of this minigame.
     *
     * @return the name as a String
     */
    String getName();

    /**
     * Gets the current state of this minigame.
     *
     * @return the current GameState
     */
    GameState getState();

    /**
     * Sets the current state of this minigame.
     *
     * @param state the new GameState
     */
    void setState(GameState state);
}

