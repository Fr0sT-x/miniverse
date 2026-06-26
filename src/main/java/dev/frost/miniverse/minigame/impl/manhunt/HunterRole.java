package dev.frost.miniverse.minigame.impl.manhunt;

import dev.frost.miniverse.minigame.core.role.Role;

public class HunterRole implements Role {
    @Override
    public String getId() {
        return "hunter";
    }

    @Override
    public String getDisplayName() {
        return "Hunter";
    }

    @Override
    public String getDescription() {
        return "Hunt down the speedrunners before they beat the game.";
    }
}
