package dev.frost.miniverse.minigame.impl.manhunt;

import dev.frost.miniverse.minigame.core.role.Role;

public class SpeedrunnerRole implements Role {
    @Override
    public String getId() {
        return "speedrunner";
    }

    @Override
    public String getDisplayName() {
        return "Speedrunner";
    }

    @Override
    public String getDescription() {
        return "Beat the game before the hunters catch you.";
    }
}
