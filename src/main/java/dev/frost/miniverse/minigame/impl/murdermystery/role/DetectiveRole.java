package dev.frost.miniverse.minigame.impl.murdermystery.role;

import dev.frost.miniverse.minigame.core.role.Role;

public class DetectiveRole implements Role {
    @Override
    public String getId() {
        return "detective";
    }

    @Override
    public String getDisplayName() {
        return "Detective";
    }

    @Override
    public String getDescription() {
        return "Find and eliminate the Murderer. Protect the innocents.";
    }
}
