package dev.frost.miniverse.minigame.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import dev.frost.miniverse.minigame.core.protection.MapProtectionTracker;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class MinigameContext {
    private final ParticipantSet participants = new ParticipantSet();
    private final GameClock clock = new GameClock();
    private final MapProtectionTracker protectionTracker = new MapProtectionTracker();

    @Nullable
    private MinecraftServer server;
    private Consumer<GameState> stateUpdater = state -> {
    };

    public void bindServer(MinecraftServer server) {
        this.server = server;
    }

    public Optional<MinecraftServer> server() {
        return Optional.ofNullable(this.server);
    }

    @Nullable
    public MinecraftServer nullableServer() {
        return this.server;
    }

    public ParticipantSet participants() {
        return this.participants;
    }

    public GameClock clock() {
        return this.clock;
    }

    public MapProtectionTracker protectionTracker() {
        return this.protectionTracker;
    }

    void attachStateUpdater(Consumer<GameState> stateUpdater) {
        this.stateUpdater = stateUpdater;
    }

    public void setState(GameState state) {
        this.stateUpdater.accept(state);
    }

    public Optional<ServerPlayerEntity> resolvePlayer(UUID playerId) {
        if (this.server == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.participants.resolve(this.server, playerId));
    }

    public List<ServerPlayerEntity> liveParticipants() {
        return this.participants.livePlayers(this.server);
    }

    public Set<UUID> participantIds() {
        return this.participants.ids();
    }
}
