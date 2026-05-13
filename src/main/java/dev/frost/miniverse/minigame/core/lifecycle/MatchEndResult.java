package dev.frost.miniverse.minigame.core.lifecycle;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record MatchEndResult(Set<UUID> winners, Text winnerLabel) {
    public static MatchEndResult winner(ServerPlayerEntity winner) {
        return new MatchEndResult(Set.of(winner.getUuid()), Text.literal(winner.getName().getString()));
    }

    public static MatchEndResult winners(Collection<ServerPlayerEntity> winners, Text winnerLabel) {
        return new MatchEndResult(
            winners.stream().map(ServerPlayerEntity::getUuid).collect(Collectors.toUnmodifiableSet()),
            winnerLabel
        );
    }

    public boolean isWinner(ServerPlayerEntity player) {
        return this.winners.contains(player.getUuid());
    }
}
