package dev.frost.miniverse.minigame.impl.murdermystery.role;

import dev.frost.miniverse.minigame.core.role.Role;

public class SpectatorRole implements Role {
    @Override
    public String getId() {
        return "spectator";
    }

    @Override
    public String getDisplayName() {
        return "Spectator";
    }

    @Override
    public String getDescription() {
        return "Watch the game unfold.";
    }

    @Override
    public boolean isSpectator() {
        return true;
    }
}
