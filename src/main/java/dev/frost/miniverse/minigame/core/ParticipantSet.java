package dev.frost.miniverse.minigame.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores participant identity by UUID and resolves live player entities only when needed.
 */
public final class ParticipantSet {
    private final Set<UUID> participantIds = ConcurrentHashMap.newKeySet();

    public void add(UUID playerId) {
        this.participantIds.add(playerId);
    }

    public void add(ServerPlayerEntity player) {
        this.add(player.getUuid());
    }

    public void remove(UUID playerId) {
        this.participantIds.remove(playerId);
    }

    public void remove(ServerPlayerEntity player) {
        this.remove(player.getUuid());
    }

    public boolean contains(UUID playerId) {
        return this.participantIds.contains(playerId);
    }

    public boolean contains(ServerPlayerEntity player) {
        return this.contains(player.getUuid());
    }

    public int size() {
        return this.participantIds.size();
    }

    public boolean isEmpty() {
        return this.participantIds.isEmpty();
    }

    public void clear() {
        this.participantIds.clear();
    }

    public Set<UUID> ids() {
        return Collections.unmodifiableSet(Set.copyOf(this.participantIds));
    }

    @Nullable
    public ServerPlayerEntity resolve(MinecraftServer server, UUID playerId) {
        if (!this.contains(playerId)) {
            return null;
        }
        return server.getPlayerManager().getPlayer(playerId);
    }

    public List<ServerPlayerEntity> livePlayers(@Nullable MinecraftServer server) {
        if (server == null) {
            return List.of();
        }

        List<ServerPlayerEntity> players = new ArrayList<>();
        for (UUID playerId : this.participantIds) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }
}
