package dev.frost.miniverse.session;

import dev.frost.miniverse.minigame.core.MinigameDefinition;

public record SessionGameDescriptor(String id, String displayName, SessionTopology topology, MinigameDefinition definition) {
    public SessionGameDescriptor {
        if (definition == null) {
            throw new IllegalArgumentException("Session game descriptor requires a minigame definition.");
        }
        id = id == null || id.isBlank() ? definition.id() : id.trim();
        displayName = displayName == null || displayName.isBlank() ? definition.displayName() : displayName.trim();
        topology = topology == null ? definition.topology() : topology;
    }

    public static SessionGameDescriptor fromDefinition(MinigameDefinition definition) {
        return new SessionGameDescriptor(definition.id(), definition.displayName(), definition.topology(), definition);
    }

    public String getCommandName() {
        return this.id;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public SessionTopology getTopology() {
        return this.topology;
    }
}
