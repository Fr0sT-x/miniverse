package dev.frost.miniverse.minigame.impl.murdermystery.role;

import dev.frost.miniverse.minigame.core.role.Role;

public class InnocentRole implements Role {
    @Override
    public String getId() {
        return "innocent";
    }

    @Override
    public String getDisplayName() {
        return "Innocent";
    }

    @Override
    public String getDescription() {
        return "Survive as long as possible. Help the Detective!";
    }
}
