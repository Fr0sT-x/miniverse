package dev.frost.miniverse.minigame.core.vanilla;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.List;

public record VanillaTeamDescriptor(
    String logicalId,
    Text displayName,
    Collection<ServerPlayerEntity> members,
    VanillaTeamOptions options
) {
    public VanillaTeamDescriptor {
        if (logicalId == null || logicalId.isBlank()) {
            throw new IllegalArgumentException("Vanilla team logical id cannot be blank.");
        }
        displayName = displayName == null ? Text.literal(logicalId) : displayName;
        members = members == null ? List.of() : List.copyOf(members);
        options = options == null ? VanillaTeamOptions.defaults() : options;
    }
}
