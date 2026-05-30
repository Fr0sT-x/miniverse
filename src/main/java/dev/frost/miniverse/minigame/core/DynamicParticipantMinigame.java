package dev.frost.miniverse.minigame.core;

import net.minecraft.server.network.ServerPlayerEntity;

public interface DynamicParticipantMinigame {
    default void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
    }

    default void removeParticipantMidGame(ServerPlayerEntity player) {
    }

    default void assignTeamMidGame(ServerPlayerEntity player, String teamId, String role) {
        this.addParticipantMidGame(player, teamId, role);
    }
}
