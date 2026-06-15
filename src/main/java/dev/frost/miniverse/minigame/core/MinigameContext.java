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
    private final SessionRoster roster = new SessionRoster();
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

    public SessionRoster roster() {
        return this.roster;
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
        return Optional.ofNullable(this.roster.resolve(this.server, playerId));
    }

    public List<ServerPlayerEntity> liveParticipants() {
        return this.roster.onlinePlayers(this.server);
    }

    public Set<UUID> participantIds() {
        return this.roster.allParticipants();
    }
}
