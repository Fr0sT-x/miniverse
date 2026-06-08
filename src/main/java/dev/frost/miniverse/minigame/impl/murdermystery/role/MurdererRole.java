package dev.frost.miniverse.minigame.impl.murdermystery.role;

import dev.frost.miniverse.minigame.core.role.Role;

public class MurdererRole implements Role {
    @Override
    public String getId() {
        return "murderer";
    }

    @Override
    public String getDisplayName() {
        return "Murderer";
    }

    @Override
    public String getDescription() {
        return "Kill all innocents. Do not get caught!";
    }
}
