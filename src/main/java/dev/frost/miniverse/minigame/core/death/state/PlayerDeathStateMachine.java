package dev.frost.miniverse.minigame.core.death.state;

import dev.frost.miniverse.minigame.core.death.DeathState;

public class PlayerDeathStateMachine {
    private DeathState currentState = DeathState.ALIVE;

    public DeathState getCurrentState() {
        return this.currentState;
    }

    /**
     * Attempts to transition to a new state.
     *
     * @param newState the target state
     * @return true if the transition was successful, false if it was invalid
     */
    public boolean transitionTo(DeathState newState) {
        if (!isValidTransition(this.currentState, newState)) {
            return false;
        }
        this.currentState = newState;
        return true;
    }

    /**
     * Forces the state machine into the DISCONNECTED state,
     * overriding normal transition rules except if already disconnected.
     *
     * @return true if the state was changed to DISCONNECTED
     */
    public boolean disconnect() {
        if (this.currentState == DeathState.DISCONNECTED) {
            return false;
        }
        this.currentState = DeathState.DISCONNECTED;
        return true;
    }

    private boolean isValidTransition(DeathState current, DeathState next) {
        if (current == DeathState.DISCONNECTED) {
            return false; // Cannot transition out of DISCONNECTED
        }

        return switch (current) {
            case ALIVE -> next == DeathState.DEATH_PROCESSING || next == DeathState.DISCONNECTED;
            case DEATH_PROCESSING -> next == DeathState.SPECTATING || next == DeathState.DISCONNECTED;
            case SPECTATING -> next == DeathState.RESPAWNING || next == DeathState.DISCONNECTED;
            case RESPAWNING -> next == DeathState.ALIVE || next == DeathState.DISCONNECTED;
            case DISCONNECTED -> false;
        };
    }
}
