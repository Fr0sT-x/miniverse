package dev.frost.miniverse.minigame.impl.duels;

import dev.frost.miniverse.minigame.arena.Arena;
import dev.frost.miniverse.minigame.core.kit.Kit;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DuelMatchContext {
    private final UUID matchId;
    private final Arena arena;
    private final Kit kit;
    private final MatchRules matchRules;
    private final List<ServerPlayerEntity> players;
    private final Instant startTime;
    private final NbtCompound metadata;

    public DuelMatchContext(UUID matchId, Arena arena, Kit kit, MatchRules matchRules, List<ServerPlayerEntity> players) {
        this.matchId = matchId;
        this.arena = arena;
        this.kit = kit;
        this.matchRules = matchRules;
        this.players = List.copyOf(players);
        this.startTime = Instant.now();
        this.metadata = new NbtCompound();
    }

    public UUID getMatchId() {
        return matchId;
    }

    public Arena getArena() {
        return arena;
    }

    public Kit getKit() {
        return kit;
    }

    public MatchRules getMatchRules() {
        return matchRules;
    }

    public List<ServerPlayerEntity> getPlayers() {
        return players;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public NbtCompound getMetadata() {
        return metadata;
    }
}
